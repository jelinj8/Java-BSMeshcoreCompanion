package cz.bliksoft.meshcorecompanion.connection;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.app.ui.actions.IconBinder;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.fx.tools.IconspecUtils;
import cz.bliksoft.javautils.fx.tools.ImageUtils;
import cz.bliksoft.meshcore.companion.BleMeshcoreCompanion;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.companion.MeshcoreCompanionBase;
import cz.bliksoft.meshcore.companion.SerialMeshcoreCompanion;
import cz.bliksoft.meshcore.companion.TCPMeshcoreCompanion;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class ConnectionManager {

	private static final Logger log = LogManager.getLogger(ConnectionManager.class);

	private static final ConnectionManager INSTANCE = new ConnectionManager();

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private final ReadOnlyBooleanWrapper disconnected = new ReadOnlyBooleanWrapper(true);
	private final ReadOnlyBooleanWrapper reconnecting = new ReadOnlyBooleanWrapper(false);
	private final ReadOnlyStringWrapper connectedDevice = new ReadOnlyStringWrapper();

	private MeshcoreCompanion companion;

	private ConnectionManager() {
		connected.addListener((obs, o, n) -> disconnected.set(!n));
	}

	public static ConnectionManager getInstance() {
		return INSTANCE;
	}

	public ReadOnlyBooleanProperty connectedProperty() {
		return connected.getReadOnlyProperty();
	}

	public ObservableBooleanValue disconnectedProperty() {
		return disconnected.getReadOnlyProperty();
	}

	public ReadOnlyBooleanProperty reconnectingProperty() {
		return reconnecting.getReadOnlyProperty();
	}

	public ReadOnlyStringProperty connectedDeviceProperty() {
		return connectedDevice.getReadOnlyProperty();
	}

	public MeshcoreCompanion getCompanion() {
		return companion;
	}

	private static Node sizedIcon(String iconKey) {
		Node icon = ImageUtils.getIconNode(IconspecUtils.getIconspec(iconKey));
		IconBinder.enforceIconSize(icon, IconspecUtils.getIconspecSize("button-size", 16));
		return icon;
	}

	private static void applyIcon(ButtonBase btn, String iconKey) {
		btn.setGraphic(sizedIcon(iconKey));
	}

	private static Node transportIcon(String transport) {
		String iconKey = switch (transport) {
		case "tcp" -> "action/network";
		case "ble" -> "action/bluetooth";
		default -> "action/usb";
		};
		return sizedIcon(iconKey);
	}

	/**
	 * True if {@code device} is a "usb"/"ble" saved device whose background
	 * availability check (see {@link #showConnectDialog}) has completed and did NOT
	 * find it currently present. Always false before that category's check finishes
	 * (never a false "unavailable" from missing information), for "tcp" devices
	 * (not checked at all), and for a device with no stored port hint to compare
	 * against.
	 */
	private static boolean isUnavailable(SavedDevice device, Set<String> availableUsb, Set<String> availableBle,
			boolean usbDone, boolean bleDone) {
		String hint = device.getPortHint();
		if (hint == null || hint.isBlank())
			return false;
		String upper = hint.toUpperCase(Locale.ROOT);
		if ("usb".equals(device.getTransport()))
			return usbDone && !availableUsb.contains(upper);
		if ("ble".equals(device.getTransport()))
			return bleDone && !availableBle.contains(upper);
		return false;
	}

	// ── Connect dialog ───────────────────────────────────────────────────────

	public void openConnectDialog() {
		showConnectDialog(DeviceRegistry.load());
	}

	private void showConnectDialog(List<SavedDevice> initialDevices) {
		ObservableList<SavedDevice> devices = FXCollections.observableArrayList(initialDevices);

		// Availability state for this dialog session only - not persisted, not a
		// SavedDevice/DeviceRegistry field. Populated in the background (see
		// dialog.setOnShown below) and read from the cell factory's updateItem, which
		// always runs on the FX thread - only these sets/flags cross the thread
		// boundary, so only they need thread-safe types.
		Set<String> availableUsbPorts = ConcurrentHashMap.newKeySet();
		Set<String> availableBleAddresses = ConcurrentHashMap.newKeySet();
		AtomicBoolean usbCheckDone = new AtomicBoolean(false);
		AtomicBoolean bleCheckDone = new AtomicBoolean(false);

		ListView<SavedDevice> listView = new ListView<>(devices);
		listView.setPrefHeight(160);
		listView.setCellFactory(lv -> new ListCell<>() {
			// Rendered via an explicit Text node (not setText(...)) because -fx-strikethrough
			// has no effect on ListCell/Labeled itself - confirmed by testing: a CSS class
			// setting both -fx-opacity and -fx-strikethrough on the cell dimmed it (opacity is
			// a plain Node property) but never struck the text through. Text.setStrikethrough(...)
			// is the real, guaranteed-to-work API for this, so drive it directly instead of
			// relying on CSS for it.
			private final Text label = new Text();
			private final HBox box = new HBox(6, label);

			{
				box.setAlignment(Pos.CENTER_LEFT);
				// Text uses -fx-fill, not -fx-text-fill, so it won't otherwise track the
				// selection-highlight/theme color setText(...) got automatically - bind it to
				// the cell's own textFillProperty (which the skin/theme CSS does drive) instead
				// of hardcoding a color.
				label.fillProperty().bind(textFillProperty());
			}

			@Override
			protected void updateItem(SavedDevice device, boolean empty) {
				super.updateItem(device, empty);
				if (empty || device == null) {
					setGraphic(null);
					getStyleClass().remove("saved-device-unavailable");
					return;
				}
				label.setText(device.getName() + "  [" + device.getPubkeyHex() + "]");
				boolean unavailable = isUnavailable(device, availableUsbPorts, availableBleAddresses,
						usbCheckDone.get(), bleCheckDone.get());
				label.setStrikethrough(unavailable);
				box.getChildren().setAll(transportIcon(device.getTransport()), label);
				if (unavailable) {
					if (!getStyleClass().contains("saved-device-unavailable"))
						getStyleClass().add("saved-device-unavailable");
				} else {
					getStyleClass().remove("saved-device-unavailable");
				}
				setGraphic(box);
			}
		});

		Button connectBtn = new Button("Connect");
		connectBtn.setDefaultButton(true);
		connectBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

		Button forgetBtn = new Button("Forget");
		forgetBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

		HBox savedButtons = new HBox(8, connectBtn, forgetBtn);
		savedButtons.setAlignment(Pos.CENTER_LEFT);

		Button newUsbBtn = new Button("USB…");
		applyIcon(newUsbBtn, "action/usb");
		Button newTcpBtn = new Button("TCP…");
		applyIcon(newTcpBtn, "action/network");
		Button newBleBtn = new Button("BLE…");
		applyIcon(newBleBtn, "action/bluetooth");

		HBox buttons = new HBox(4, new Label("Connect new:"), newUsbBtn, newTcpBtn, newBleBtn);
		buttons.setAlignment(Pos.CENTER_LEFT);

		VBox content = new VBox(8, new Label("Known devices:"), listView, savedButtons, new Separator(), buttons);
		content.setPadding(new Insets(8));

		Dialog<Void> dialog = new Dialog<>();
		dialog.setTitle("Connect");
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		dialog.initOwner(BSAppUI.getStage());

		listView.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2 && !connectBtn.isDisabled())
				connectBtn.fire();
		});
		listView.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER && !connectBtn.isDisabled()) {
				connectBtn.fire();
				e.consume();
			} else if (e.getCode() == KeyCode.ESCAPE) {
				dialog.close();
				e.consume();
			}
		});

		forgetBtn.setOnAction(e -> {
			SavedDevice selected = listView.getSelectionModel().getSelectedItem();
			if (selected != null) {
				devices.remove(selected);
				DeviceRegistry.remove(selected);
			}
		});

		connectBtn.setOnAction(e -> {
			SavedDevice selected = listView.getSelectionModel().getSelectedItem();
			if (selected == null)
				return;
			dialog.close();
			String portHint = selected.getPortHint();
			if ("tcp".equals(selected.getTransport())) {
				if (portHint != null && portHint.contains(":")) {
					int lastColon = portHint.lastIndexOf(':');
					String host = portHint.substring(0, lastColon);
					int port;
					try {
						port = Integer.parseInt(portHint.substring(lastColon + 1));
					} catch (NumberFormatException ex) {
						showTcpDialog();
						return;
					}
					connectTcp(host, port);
				} else {
					showTcpDialog();
				}
			} else if ("ble".equals(selected.getTransport())) {
				if (portHint != null && !portHint.isBlank()) {
					// connectBle() shows its own modal wait dialog via BSAppUI.executeWaiting();
					// deferring lets this dialog finish closing first, same as the "new BLE..."
					// path below - opening it in the same pulse produced a stuck, unpainted dialog.
					Platform.runLater(() -> connectBle(portHint));
				} else {
					pickNewBleDevice();
				}
			} else {
				if (portHint != null && !portHint.isBlank()) {
					connectSerial(portHint, 115200, false);
				} else {
					pickNewSerialPort();
				}
			}
		});

		newUsbBtn.setOnAction(e -> {
			dialog.close();
			Platform.runLater(this::pickNewSerialPort);
		});

		newTcpBtn.setOnAction(e -> {
			dialog.close();
			Platform.runLater(this::showTcpDialog);
		});

		newBleBtn.setOnAction(e -> {
			dialog.close();
			Platform.runLater(this::pickNewBleDevice);
		});

		boolean hasUsb = devices.stream().anyMatch(d -> "usb".equals(d.getTransport()));
		boolean hasBle = devices.stream().anyMatch(d -> "ble".equals(d.getTransport()));

		// Background availability probes, kicked off once the dialog has actually
		// painted (same setOnShown hook ContactChatPane uses for its own post-show
		// work) - silent and best-effort, so a failure here (Bluetooth off, sidecar
		// unavailable, ...) just leaves that category's devices unstruck rather than
		// surfacing an error or marking everything unavailable. Two independent
		// threads, not one sequential pass, so the near-instant COM-port result can
		// update the list well before the multi-second BLE scan finishes.
		dialog.setOnShown(e -> {
			if (hasUsb) {
				new Thread(() -> {
					List<String> ports;
					try {
						ports = SerialMeshcoreCompanion.listPorts();
					} catch (Exception ex) {
						log.debug("Saved-device availability: listing serial ports failed", ex);
						return;
					}
					for (String p : ports)
						availableUsbPorts.add(p.split(" – ")[0].trim().toUpperCase(Locale.ROOT));
					usbCheckDone.set(true);
					Platform.runLater(listView::refresh);
				}, "saved-device-usb-availability").start();
			}
			if (hasBle) {
				new Thread(() -> {
					List<String> found;
					try {
						// Same duration as pickNewBleDevice's user-initiated scan - a shorter probe
						// (previously 3000ms) missed devices that were actually in range and
						// advertising, likely due to normal BLE advertisement-interval/duty-cycle
						// variance; this runs silently in the background so the extra couple of
						// seconds before a result lands doesn't cost the user anything.
						found = BleMeshcoreCompanion.scanForNusDevices(5000);
					} catch (Exception ex) {
						log.debug("Saved-device availability: BLE scan failed", ex);
						return;
					}
					for (String s : found) {
						String addr = s.contains(" ") ? s.substring(0, s.indexOf(' ')).trim() : s.trim();
						availableBleAddresses.add(addr.toUpperCase(Locale.ROOT));
					}
					bleCheckDone.set(true);
					Platform.runLater(listView::refresh);
				}, "saved-device-ble-availability").start();
			}
		});

		dialog.showAndWait();
	}

	private void pickNewSerialPort() {
		new Thread(() -> {
			// SerialMeshcoreCompanion.listPorts() (not jSerialComm's SerialPort directly)
			// keeps this class from depending on jSerialComm itself - same reasoning as
			// the BLE side (see pickNewBleDevice): Meshcore is meant to stay usable
			// without forcing every transport-specific dependency on every consumer.
			List<String> ports = SerialMeshcoreCompanion.listPorts();
			Platform.runLater(() -> showPortSelectionDialog(ports));
		}, "serial-port-scan").start();
	}

	private void showPortSelectionDialog(List<String> ports) {
		if (ports.isEmpty()) {
			Alert alert = new Alert(Alert.AlertType.WARNING);
			alert.setTitle("Connect");
			alert.setHeaderText("No serial ports found");
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
			return;
		}

		ChoiceDialog<String> dialog = new ChoiceDialog<>(ports.get(0), ports);
		dialog.setTitle("New USB connection");
		dialog.setHeaderText("Select serial port");
		dialog.setContentText("Port:");
		dialog.initOwner(BSAppUI.getStage());

		dialog.showAndWait().ifPresent(choice -> {
			String portName = choice.split(" – ")[0].trim();
			connectSerial(portName, 115200, false);
		});
	}

	private void showTcpDialog() {
		TextField hostField = new TextField();
		hostField.setPromptText("host or IP address");

		TextField portField = new TextField();
		portField.setPromptText("port");
		portField.textProperty().addListener((obs, o, n) -> {
			if (!n.matches("\\d*"))
				portField.setText(n.replaceAll("[^\\d]", ""));
		});

		Button connectBtn = new Button("Connect");
		connectBtn.setDefaultButton(true);
		connectBtn.disableProperty().bind(hostField.textProperty().isEmpty().or(portField.textProperty().isEmpty()));

		VBox content = new VBox(8, new Label("Host / IP:"), hostField, new Label("Port:"), portField, connectBtn);
		content.setPadding(new Insets(8));

		Dialog<Void> dialog = new Dialog<>();
		dialog.setTitle("New TCP connection");
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		dialog.initOwner(BSAppUI.getStage());

		connectBtn.setOnAction(e -> {
			String host = hostField.getText().trim();
			String portText = portField.getText().trim();
			if (host.isBlank() || portText.isBlank())
				return;
			int port;
			try {
				port = Integer.parseInt(portText);
			} catch (NumberFormatException ex) {
				return;
			}
			dialog.close();
			connectTcp(host, port);
		});

		dialog.showAndWait();
	}

	private void pickNewBleDevice() {
		AtomicReference<BleMeshcoreCompanion.NusScanResult> result = new AtomicReference<>();
		AtomicReference<IOException> error = new AtomicReference<>();

		BSAppUI.executeWaiting(() -> {
			try {
				// Not try-with-resources: kept open so a picked device can connect on this
				// same adapter below instead of opening a second sidecar process and
				// re-scanning for an address this scan already found - see
				// showBleDeviceSelectionDialog/connectBle. NusScanResult (not BleAdapter
				// directly) keeps this class from depending on BSToolbox-BLE itself - the
				// Meshcore library is meant to stay usable (e.g. TCP-only) without forcing
				// that dependency on every consumer.
				result.set(BleMeshcoreCompanion.scanForNusDevicesKeepingAdapter(5000));
			} catch (IOException e) {
				error.set(e);
			}
		}, "Add BLE Companion", "Scanning for devices…", null);

		showBleDeviceSelectionDialog(result.get(), error.get());
	}

	/**
	 * @param scan handle for the scan that ran, or {@code null} if it failed before
	 *             producing one. Handed off to {@link #connectBle} if the user
	 *             picks a device; closed here in every other outcome (no devices
	 *             found, or the user cancels).
	 */
	private void showBleDeviceSelectionDialog(BleMeshcoreCompanion.NusScanResult scan, IOException scanError) {
		if (scanError != null) {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("BLE Scan");
			alert.setHeaderText("Bluetooth scan failed");
			alert.setContentText(scanError.getMessage());
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
			return;
		}
		List<String> devices = scan.getDevices();
		if (devices.isEmpty()) {
			scan.close();
			Alert alert = new Alert(Alert.AlertType.WARNING);
			alert.setTitle("BLE Scan");
			alert.setHeaderText("No BLE devices found");
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
			return;
		}

		ChoiceDialog<String> dialog = new ChoiceDialog<>(devices.get(0), devices);
		dialog.setTitle("New BLE connection");
		dialog.setHeaderText("Select BLE device");
		dialog.setContentText("Device:");
		dialog.initOwner(BSAppUI.getStage());

		dialog.showAndWait().ifPresentOrElse(choice -> {
			// format: "AA:BB:CC:DD:EE:FF (name)"
			String address = choice.contains(" ") ? choice.substring(0, choice.indexOf(' ')).trim() : choice.trim();
			connectBle(address, scan);
		}, scan::close);
	}

	/**
	 * Reconnects to a previously saved device - opens its own adapter and scans.
	 */
	private void connectBle(String address) {
		connectBle(address, null);
	}

	/**
	 * @param scan a handle from a scan that already found {@code address} (see
	 *             {@link #pickNewBleDevice}), reused via
	 *             {@link BleMeshcoreCompanion.NusScanResult#connect} instead of
	 *             opening a new adapter and scanning again; or {@code null} to do
	 *             that as usual.
	 */
	private void connectBle(String address, BleMeshcoreCompanion.NusScanResult scan) {
		AtomicReference<BleMeshcoreCompanion> result = new AtomicReference<>();
		AtomicReference<Exception> error = new AtomicReference<>();

		BSAppUI.executeWaiting(() -> {
			BleMeshcoreCompanion c = null;
			try {
				c = scan != null ? scan.connect("BSMeshcoreCompanion", address)
						: new BleMeshcoreCompanion("BSMeshcoreCompanion", address);
				// A healthy connect completes in a few seconds; this budget exists for the
				// pathological-but-eventually-successful case (pre-connect scan ~5s, connect
				// ~23.5s worst case, subscribe up to DEFAULT_TIMEOUT_MS - see BlePeripheral).
				// Trimmed down from an earlier 110s: BleMeshcoreCompanion retries the whole
				// scan-connect cycle internally on failure (e.g. an unpaired device, which will
				// never succeed until paired via the OS's own Bluetooth settings - see
				// BleMeshcoreCompanion's class doc), so the old budget let several full failed
				// cycles stack up, each logging its own "needs pairing" warning, before finally
				// timing out here. 60s still comfortably covers one worst-case successful cycle
				// -
				// and, on platforms where the OS pops its own interactive pairing/PIN prompt
				// during connect (confirmed NOT the case on Windows - pairing there only
				// happens
				// via Bluetooth settings beforehand), leaves room to respond to it.
				c.awaitAvailable(60000L);
				result.set(c);
			} catch (TimeoutException | InterruptedException e) {
				if (c != null)
					c.close();
				error.set(e);
			}
		}, null, "Connecting…", address);

		Exception e = error.get();
		if (e != null) {
			log.error("BLE connection to {} failed", address, e);
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("Connection failed");
			alert.setHeaderText("Could not connect to " + address);
			alert.setContentText(e.getMessage());
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
			return;
		}

		BleMeshcoreCompanion c = result.get();
		c.addAvailabilityListener(new MeshcoreCompanionBase.AvailabilityListener() {
			public void onAvailable(MeshcoreCompanionBase companion) {
				Platform.runLater(() -> reconnecting.set(false));
			}

			public void onUnavailable(MeshcoreCompanionBase companion) {
				Platform.runLater(() -> reconnecting.set(true));
			}
		});
		c.installAutosyncMessages();
		companion = c;
		autoSaveDevice(c, address, "ble");
		String deviceLabel = buildDeviceLabel(c, address);
		connected.set(true);
		connectedDevice.set(deviceLabel);
		Context.getCurrentContext().put(MeshcoreCompanion.class, c);
		BSAppUI.showStatusMessage("Connected to " + address);
		log.info("BLE connected to {}", address);
	}

	private void connectTcp(String host, int port) {
		new Thread(() -> {
			MeshcoreCompanionBase created = null;
			try {
				TCPMeshcoreCompanion c = new TCPMeshcoreCompanion("BSMeshcoreCompanion", host, port);
				created = c;
				c.awaitAvailable(2000L);
				c.addAvailabilityListener(new MeshcoreCompanionBase.AvailabilityListener() {
					public void onAvailable(MeshcoreCompanionBase companion) {
						Platform.runLater(() -> reconnecting.set(false));
					}

					public void onUnavailable(MeshcoreCompanionBase companion) {
						Platform.runLater(() -> reconnecting.set(true));
					}
				});
				c.installAutosyncMessages();
				companion = c;

				String hint = host + ":" + port;
				autoSaveDevice(c, hint, "tcp");
				String deviceLabel = buildDeviceLabel(c, hint);

				Platform.runLater(() -> {
					connected.set(true);
					connectedDevice.set(deviceLabel);
					Context.getCurrentContext().put(MeshcoreCompanion.class, c);
					BSAppUI.showStatusMessage("Connected to " + hint);
					log.info("Connected to {}", hint);
				});
			} catch (TimeoutException | InterruptedException | IOException e) {
				if (created != null)
					created.close();
				String hint = host + ":" + port;
				log.error("Connection to {} failed", hint, e);
				Platform.runLater(() -> {
					Alert alert = new Alert(Alert.AlertType.ERROR);
					alert.setTitle("Connection failed");
					alert.setHeaderText("Could not connect to " + hint);
					alert.setContentText(e.getMessage());
					alert.initOwner(BSAppUI.getStage());
					alert.showAndWait();
				});
			}
		}, "meshcore-connect").start();
	}

	private void connectSerial(String portName, int baud, boolean unused) {
		new Thread(() -> {
			MeshcoreCompanionBase created = null;
			try {
				SerialMeshcoreCompanion c = new SerialMeshcoreCompanion("BSMeshcoreCompanion", portName, baud);
				created = c;
				c.awaitAvailable(2000L);
				c.addAvailabilityListener(new MeshcoreCompanionBase.AvailabilityListener() {
					public void onAvailable(MeshcoreCompanionBase companion) {
						Platform.runLater(() -> reconnecting.set(false));
					}

					public void onUnavailable(MeshcoreCompanionBase companion) {
						Platform.runLater(() -> reconnecting.set(true));
					}
				});
				c.installAutosyncMessages();
				companion = c;

				autoSaveDevice(c, portName, "usb");
				String deviceLabel = buildDeviceLabel(c, portName);

				Platform.runLater(() -> {
					connected.set(true);
					connectedDevice.set(deviceLabel);
					Context.getCurrentContext().put(MeshcoreCompanion.class, c);
					BSAppUI.showStatusMessage("Connected to " + portName);
					log.info("Connected to {}", portName);
				});
			} catch (TimeoutException | InterruptedException | IOException e) {
				if (created != null)
					created.close();
				log.error("Connection to {} failed", portName, e);
				Platform.runLater(() -> {
					Alert alert = new Alert(Alert.AlertType.ERROR);
					alert.setTitle("Connection failed");
					alert.setHeaderText("Could not connect to " + portName);
					alert.setContentText(e.getMessage());
					alert.initOwner(BSAppUI.getStage());
					alert.showAndWait();
				});
			}
		}, "meshcore-connect").start();
	}

	private void autoSaveDevice(MeshcoreCompanion c, String hint, String transport) {
		var si = c.getSelfInfo();
		if (si == null)
			return;
		String pubHex = MeshcoreUtils.hex(Arrays.copyOf(si.getPubkey(), 6));
		String nodeName = si.getNodeName();
		String name = (nodeName != null && !nodeName.isBlank()) ? nodeName : pubHex;
		DeviceRegistry.addOrUpdate(new SavedDevice(name, pubHex, hint, transport));
		log.info("Device saved: {} [{}] via {} on {}", name, pubHex, transport, hint);
	}

	private String buildDeviceLabel(MeshcoreCompanion c, String hint) {
		var si = c.getSelfInfo();
		if (si == null)
			return hint;
		String pubHex = MeshcoreUtils.hex(Arrays.copyOf(si.getPubkey(), 6));
		String nodeName = si.getNodeName();
		String name = (nodeName != null && !nodeName.isBlank()) ? nodeName : pubHex;
		return name + " [" + pubHex + "]";
	}

	// ── Disconnect ───────────────────────────────────────────────────────────

	public void disconnect() {
		MeshcoreCompanion c = companion;
		if (c == null)
			return;
		companion = null;
		connected.set(false);
		reconnecting.set(false);
		connectedDevice.set(null);
		Context.getCurrentContext().remove(MeshcoreCompanion.class);
		BSAppUI.showStatusMessage("Disconnected");
		log.info("Disconnected");
		// c.close() can block for several seconds on a BLE round-trip to the sidecar
		// (see
		// BlePeripheral's CONNECT_TIMEOUT_MS) - disconnect() is often called on the FX
		// thread
		// (including app shutdown via AppClosedEvent), so run the close off-thread
		// rather than
		// freezing the UI on it. Daemon so it can't hold up JVM exit either; the
		// sidecar process
		// exits on its own once the JVM's end of its stdin pipe closes, even if this
		// doesn't
		// finish first.
		Thread closer = new Thread(() -> {
			try {
				c.close();
			} catch (Exception e) {
				log.warn("Error during disconnect", e);
			}
		}, "meshcore-disconnect");
		closer.setDaemon(true);
		closer.start();
	}
}
