package cz.bliksoft.meshcorecompanion.controls;

import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.SelfInfo;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import cz.bliksoft.meshcore.utils.ShareLinks;
import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class ConnectionStatusLabel extends Label {

	private final ContextMenu contextMenu;

	public ConnectionStatusLabel() {
		MenuItem copyKeyItem = new MenuItem("Copy key");
		copyKeyItem.setOnAction(e -> {
			SelfInfo si = selfInfo();
			if (si == null)
				return;
			ClipboardContent cc = new ClipboardContent();
			cc.putString(MeshcoreUtils.hex(si.getPubkey()));
			Clipboard.getSystemClipboard().setContent(cc);
		});
		MenuItem shareItem = new MenuItem("Share");
		shareItem.setOnAction(e -> {
			SelfInfo si = selfInfo();
			if (si != null)
				ShareDialog.show("Share this device", si.getNodeName(), ShareLinks.contact(si));
		});
		contextMenu = new ContextMenu(copyKeyItem, shareItem);
		// self info arrives shortly after the connection is up
		contextMenu.setOnShowing(e -> {
			boolean missing = selfInfo() == null;
			copyKeyItem.setDisable(missing);
			shareItem.setDisable(missing);
		});

		ConnectionManager cm = ConnectionManager.getInstance();
		cm.connectedProperty().addListener((obs, o, n) -> update(cm));
		cm.connectedDeviceProperty().addListener((obs, o, n) -> update(cm));
		cm.reconnectingProperty().addListener((obs, o, n) -> update(cm));
		update(cm);
	}

	private static SelfInfo selfInfo() {
		MeshcoreCompanion c = ConnectionManager.getInstance().getCompanion();
		return c != null ? c.getSelfInfo() : null;
	}

	private void update(ConnectionManager cm) {
		if (!cm.connectedProperty().get()) {
			setText("Disconnected");
			setContextMenu(null);
			return;
		}
		setContextMenu(contextMenu);
		String device = cm.connectedDeviceProperty().get();
		String base = device != null ? "Connected — " + device : "Connected";
		setText(cm.reconnectingProperty().get() ? base + " (reconnecting…)" : base);
	}
}
