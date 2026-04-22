package cz.bliksoft.meshcorecompanion.connection;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fazecast.jSerialComm.SerialPort;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.companion.SerialMeshcoreCompanion;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;

public class ConnectionManager {

    private static final Logger log = LogManager.getLogger(ConnectionManager.class);

    private static final ConnectionManager INSTANCE = new ConnectionManager();

    private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper disconnected = new ReadOnlyBooleanWrapper(true);

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

    public MeshcoreCompanion getCompanion() {
        return companion;
    }

    public void openConnectDialog() {
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
        dialog.setTitle("Connect");
        dialog.setHeaderText("Select serial port");
        dialog.setContentText("Port:");
        dialog.initOwner(BSAppUI.getStage());

        dialog.showAndWait().ifPresent(choice -> {
            String portName = choice.split(" – ")[0].trim();
            connectSerial(portName, 115200);
        });
    }

    private void connectSerial(String portName, int baud) {
        new Thread(() -> {
            try {
                SerialMeshcoreCompanion c = new SerialMeshcoreCompanion("BSMeshcoreCompanion", portName, baud);
                c.installAutosyncMessages();
                try {
                    c.awaitAvailable(5000L);
                    c.getConfig().setDeviceTime(null);
                } catch (Exception e) {
                    log.warn("Device time sync failed", e);
                }
                companion = c;
                Platform.runLater(() -> {
                    connected.set(true);
                    Context.getCurrentContext().put(MeshcoreCompanion.class, c);
                    BSAppUI.showStatusMessage("Connected to " + portName);
                    log.info("Connected to {}", portName);
                });
            } catch (IOException e) {
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

    public void disconnect() {
        MeshcoreCompanion c = companion;
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            log.warn("Error during disconnect", e);
        }
        companion = null;
        connected.set(false);
        Context.getCurrentContext().remove(MeshcoreCompanion.class);
        BSAppUI.showStatusMessage("Disconnected");
        log.info("Disconnected");
    }
}
