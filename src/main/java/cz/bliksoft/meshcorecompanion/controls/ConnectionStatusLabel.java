package cz.bliksoft.meshcorecompanion.controls;

import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.scene.control.Label;

public class ConnectionStatusLabel extends Label {

    public ConnectionStatusLabel() {
        setText("Disconnected");
        ConnectionManager.getInstance().connectedProperty().addListener((obs, o, connected) ->
                setText(connected ? "Connected" : "Disconnected"));
    }
}
