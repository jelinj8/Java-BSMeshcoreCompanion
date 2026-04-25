package cz.bliksoft.meshcorecompanion.controls;

import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.scene.control.Label;

public class ConnectionStatusLabel extends Label {

	public ConnectionStatusLabel() {
		ConnectionManager cm = ConnectionManager.getInstance();
		cm.connectedProperty().addListener((obs, o, n) -> update(cm));
		cm.connectedDeviceProperty().addListener((obs, o, n) -> update(cm));
		update(cm);
	}

	private void update(ConnectionManager cm) {
		if (!cm.connectedProperty().get()) {
			setText("Disconnected");
			return;
		}
		String device = cm.connectedDeviceProperty().get();
		setText(device != null ? "Connected — " + device : "Connected");
	}
}
