package cz.bliksoft.meshcorecompanion.controls;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.fx.controls.QRLabel;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Shows a {@code meshcore://} share link as a QR code, with the link to copy. */
public final class ShareDialog {

	private ShareDialog() {
	}

	public static void show(String title, String name, String link) {
		QRLabel qr = new QRLabel();
		qr.setModulusMultiplier(6);
		qr.setQuietZone(4);
		qr.setText(link);

		TextField linkField = new TextField(link);
		linkField.setEditable(false);
		HBox.setHgrow(linkField, Priority.ALWAYS);
		Button copyBtn = new Button("Copy link");
		copyBtn.setOnAction(e -> {
			ClipboardContent cc = new ClipboardContent();
			cc.putString(link);
			Clipboard.getSystemClipboard().setContent(cc);
		});

		VBox content = new VBox(10, qr, new HBox(8, linkField, copyBtn));
		content.setAlignment(Pos.CENTER);

		Alert dlg = new Alert(Alert.AlertType.INFORMATION);
		dlg.setTitle(title);
		dlg.setHeaderText(name);
		dlg.setGraphic(null);
		dlg.getDialogPane().setContent(content);
		dlg.initOwner(BSAppUI.getStage());
		dlg.showAndWait();
	}
}
