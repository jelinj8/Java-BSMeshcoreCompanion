package cz.bliksoft.meshcorecompanion.connection;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fazecast.jSerialComm.SerialPort;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.context.Context;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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

	// ── Connect dialog ───────────────────────────────────────────────────────

	public void openConnectDialog() {
		List<SavedDevice> saved = DeviceRegistry.load();
		if (saved.isEmpty()) {
			pickNewSerialPort();
		} else {
			showConnectDialog(saved);
		}
	}

	private void showConnectDialog(List<SavedDevice> initialDevices) {
		ObservableList<SavedDevice> devices = FXCollections.observableArrayList(initialDevices);

		ListView<SavedDevice> listView = new ListView<>(devices);
		listView.setPrefHeight(160);

		Button connectBtn = new Button("Connect");
		connectBtn.setDefaultButton(true);
		connectBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

		Button forgetBtn = new Button("Forget");
		forgetBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

		HBox savedButtons = new HBox(8, connectBtn, forgetBtn);
		savedButtons.setAlignment(Pos.CENTER_LEFT);

		Button newUsbBtn = new Button("USB…");
		Button newTcpBtn = new Button("TCP…");
		Button newBleBtn = new Button("BLE…");

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
					connectBle(portHint);
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

		dialog.showAndWait();
	}

	private void pickNewSerialPort() {
		new Thread(() -> {
			SerialPort[] ports = SerialPort.getCommPorts();
			Platform.runLater(() -> showPortSelectionDialog(ports));
		}, "serial-port-scan").start();
	}

	private void showPortSelectionDialog(SerialPort[] ports) {
		if (ports.length == 0) {
			Alert alert = new Alert(Alert.AlertType.WARNING);
			alert.setTitle("Connect");
			alert.setHeaderText("No serial ports found");
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
			return;
		}

		String[] portNames = new String[ports.length];
		for (int i = 0; i < ports.length; i++) {
			portNames[i] = ports[i].getSystemPortName() + " – " + ports[i].getDescriptivePortName();
		}

		ChoiceDialog<String> dialog = new ChoiceDialog<>(portNames[0], portNames);
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
		AtomicReference<List<String>> result = new AtomicReference<>(List.of());
		AtomicReference<IOException> error = new AtomicReference<>();

		BSAppUI.executeWaiting(() -> {
			try {
				result.set(BleMeshcoreCompanion.scanForNusDevices(5000));
			} catch (IOException e) {
				error.set(e);
			}
		}, "Add BLE Companion", "Scanning for devices…", null);

		showBleDeviceSelectionDialog(result.get(), error.get());
	}

	private void showBleDeviceSelectionDialog(List<String> devices, IOException scanError) {
		if (scanError != null) {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("BLE Scan");
			alert.setHeaderText("Bluetooth scan failed");
			alert.setContentText(scanError.getMessage());
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
			return;
		}
		if (devices.isEmpty()) {
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

		dialog.showAndWait().ifPresent(choice -> {
			// format: "AA:BB:CC:DD:EE:FF (name)"
			String address = choice.contains(" ") ? choice.substring(0, choice.indexOf(' ')).trim() : choice.trim();
			connectBle(address);
		});
	}

	private void connectBle(String address) {
		AtomicReference<BleMeshcoreCompanion> result = new AtomicReference<>();
		AtomicReference<Exception> error = new AtomicReference<>();

		BSAppUI.executeWaiting(() -> {
			BleMeshcoreCompanion c = null;
			try {
				c = new BleMeshcoreCompanion("BSMeshcoreCompanion", address);
				// BLE connect includes an internal 5-second scan; allow extra time
				c.awaitAvailable(12000L);
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
		try {
			c.close();
		} catch (Exception e) {
			log.warn("Error during disconnect", e);
		}
		companion = null;
		connected.set(false);
		reconnecting.set(false);
		connectedDevice.set(null);
		Context.getCurrentContext().remove(MeshcoreCompanion.class);
		BSAppUI.showStatusMessage("Disconnected");
		log.info("Disconnected");
	}
}
