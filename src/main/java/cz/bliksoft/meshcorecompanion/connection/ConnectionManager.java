package cz.bliksoft.meshcorecompanion.connection;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fazecast.jSerialComm.SerialPort;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.companion.MeshcoreCompanionBase;
import cz.bliksoft.meshcore.companion.SerialMeshcoreCompanion;
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

		Button newUsbBtn = new Button("New USB connection…");

		VBox content = new VBox(8, new Label("Known devices:"), listView, savedButtons, new Separator(), newUsbBtn);
		content.setPadding(new Insets(8));

		Dialog<Void> dialog = new Dialog<>();
		dialog.setTitle("Connect");
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		dialog.initOwner(BSAppUI.getStage());

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
			if (portHint != null && !portHint.isBlank()) {
				// Try the last-known port directly; if it fails the error dialog appears
				connectSerial(portHint, 115200, false);
			} else {
				pickNewSerialPort();
			}
		});

		newUsbBtn.setOnAction(e -> {
			dialog.close();
			pickNewSerialPort();
		});

		dialog.showAndWait();
	}

	private void pickNewSerialPort() {
		SerialPort[] ports = SerialPort.getCommPorts();
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

	private void connectSerial(String portName, int baud, boolean unused) {
		new Thread(() -> {
			try {
				SerialMeshcoreCompanion c = new SerialMeshcoreCompanion("BSMeshcoreCompanion", portName, baud);
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

				// Auto-save/update device by pubkey
				autoSaveDevice(c, portName);
				String deviceLabel = buildDeviceLabel(c, portName);

				Platform.runLater(() -> {
					connected.set(true);
					connectedDevice.set(deviceLabel);
					Context.getCurrentContext().put(MeshcoreCompanion.class, c);
					BSAppUI.showStatusMessage("Connected to " + portName);
					log.info("Connected to {}", portName);
				});
			} catch (TimeoutException | InterruptedException | IOException e) {
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

	private void autoSaveDevice(SerialMeshcoreCompanion c, String portName) {
		var si = c.getSelfInfo();
		if (si == null)
			return;
		String pubHex = MeshcoreUtils.hex(Arrays.copyOf(si.getPubkey(), 6));
		String nodeName = si.getNodeName();
		String name = (nodeName != null && !nodeName.isBlank()) ? nodeName : pubHex;
		DeviceRegistry.addOrUpdate(new SavedDevice(name, pubHex, portName));
		log.info("Device saved: {} [{}] on {}", name, pubHex, portName);
	}

	private String buildDeviceLabel(SerialMeshcoreCompanion c, String portName) {
		var si = c.getSelfInfo();
		if (si == null)
			return portName;
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
