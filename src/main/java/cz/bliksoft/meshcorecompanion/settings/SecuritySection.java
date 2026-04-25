package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.DeviceInfo;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import cz.bliksoft.javautils.app.ui.BSAppUI;

class SecuritySection extends VBox {

	private static final Logger log = LogManager.getLogger(SecuritySection.class);
	private static final long NO_PIN = 1_000_000L;

	private final Label pinStatusLabel = new Label("PIN: unknown");
	private final TextField pinField = new TextField();
	private final Button setPinBtn = new Button("Set PIN");
	private final Button clearPinBtn = new Button("Clear PIN");

	private final Button exportKeyBtn = new Button("Export private key…");
	private final Button importKeyBtn = new Button("Import private key…");

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private MeshcoreCompanion currentCompanion;

	SecuritySection() {
		setPadding(new Insets(0));
		setSpacing(8);

		pinField.setPromptText("6-digit PIN");
		pinField.setPrefColumnCount(8);

		getChildren().addAll(new Label("Security:"), pinStatusLabel, new HBox(6, pinField, setPinBtn, clearPinBtn),
				new HBox(6, exportKeyBtn, importKeyBtn));

		setDisable(true);

		setPinBtn.setOnAction(e -> doSetPin());
		clearPinBtn.setOnAction(e -> doClearPin());
		exportKeyBtn.setOnAction(e -> doExportKey());
		importKeyBtn.setOnAction(e -> doImportKey());

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "SecuritySection") {
					@Override
					public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
						currentCompanion = event.getNewValue();
						Platform.runLater(() -> onCompanionChanged(currentCompanion));
					}
				});

		var search = Context.getCurrentContext().getValue(MeshcoreCompanion.class);
		if (search.isValid() && search.getResult() instanceof MeshcoreCompanion existing) {
			currentCompanion = existing;
			Platform.runLater(() -> onCompanionChanged(currentCompanion));
		}
	}

	private void onCompanionChanged(MeshcoreCompanion companion) {
		if (companion == null) {
			connected.set(false);
			setDisable(true);
			pinStatusLabel.setText("PIN: unknown");
			return;
		}
		connected.set(true);
		setDisable(false);
		refreshPinStatus();
	}

	private void refreshPinStatus() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		DeviceInfo di = c.getConfig().getDeviceInfo();
		if (di != null) {
			long pin = di.getBlePIN();
			Platform.runLater(() -> pinStatusLabel
					.setText(pin >= NO_PIN ? "PIN: not set" : String.format("PIN: set (%06d)", pin)));
		}
	}

	private void doSetPin() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		String text = pinField.getText().strip();
		long pin;
		try {
			pin = Long.parseLong(text);
		} catch (NumberFormatException ex) {
			showError("Invalid PIN", "Enter a numeric PIN between 0 and 999999.");
			return;
		}
		if (pin < 0 || pin >= NO_PIN) {
			showError("Invalid PIN", "PIN must be between 0 and 999999.");
			return;
		}
		long finalPin = pin;
		new Thread(() -> {
			try {
				c.getConfig().setDevicePIN(finalPin);
				Platform.runLater(() -> {
					pinField.clear();
					pinStatusLabel.setText(String.format("PIN: set (%06d)", finalPin));
				});
			} catch (Exception ex) {
				log.error("Failed to set PIN", ex);
				Platform.runLater(() -> showError("PIN error", ex.getMessage()));
			}
		}, "pin-set").start();
	}

	private void doClearPin() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setDevicePIN(null);
				Platform.runLater(() -> pinStatusLabel.setText("PIN: not set"));
			} catch (Exception ex) {
				log.error("Failed to clear PIN", ex);
				Platform.runLater(() -> showError("PIN error", ex.getMessage()));
			}
		}, "pin-clear").start();
	}

	private void doExportKey() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				byte[] key = c.getConfig().getPrivateKey();
				if (key == null) {
					Platform.runLater(
							() -> showError("Export failed", "Private key export is disabled on this device."));
					return;
				}
				String hex = MeshcoreUtils.hex(key);
				Platform.runLater(() -> showKeyExportDialog(hex));
			} catch (UnsupportedOperationException ex) {
				Platform.runLater(() -> showError("Export failed", "Private key export is disabled on this device."));
			} catch (Exception ex) {
				log.error("Failed to export private key", ex);
				Platform.runLater(() -> showError("Export failed", ex.getMessage()));
			}
		}, "key-export").start();
	}

	private void showKeyExportDialog(String hex) {
		TextArea area = new TextArea(hex);
		area.setEditable(false);
		area.setWrapText(true);
		area.setPrefRowCount(4);

		ScrollPane scroll = new ScrollPane(area);
		scroll.setFitToWidth(true);

		Alert dialog = new Alert(Alert.AlertType.INFORMATION);
		dialog.setTitle("Private key export");
		dialog.setHeaderText("Device private key (Ed25519, 64 bytes)");
		dialog.setContentText("Store this key securely. Anyone with it can impersonate this node.");
		dialog.getDialogPane().setExpandableContent(scroll);
		dialog.getDialogPane().setExpanded(true);
		dialog.initOwner(BSAppUI.getStage());
		dialog.showAndWait();
	}

	private void doImportKey() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;

		TextField hexField = new TextField();
		hexField.setPromptText("128-character hex string (64 bytes)");
		hexField.setPrefColumnCount(36);

		VBox content = new VBox(8,
				new Label("WARNING: Importing a private key changes the node's identity.\n"
						+ "All contacts will lose the association to this node.\n" + "This cannot be undone."),
				new Label("Paste the 128-character hex private key:"), hexField);
		content.setPadding(new Insets(8, 0, 0, 0));

		Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
		dialog.setTitle("Import private key");
		dialog.setHeaderText("Import a private key onto this device");
		dialog.getDialogPane().setContent(content);
		dialog.initOwner(BSAppUI.getStage());

		dialog.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK)
				return;
			String hex = hexField.getText().strip();
			if (hex.length() != 128) {
				showError("Invalid key", "Key must be exactly 128 hex characters (64 bytes).");
				return;
			}
			byte[] key;
			try {
				key = MeshcoreUtils.fromHex(hex);
			} catch (Exception ex) {
				showError("Invalid key", "Key contains invalid hex characters.");
				return;
			}
			new Thread(() -> {
				try {
					c.getConfig().importPrivateKey(key);
					Platform.runLater(() -> {
						Alert ok = new Alert(Alert.AlertType.INFORMATION);
						ok.setTitle("Import complete");
						ok.setHeaderText("Private key imported successfully.");
						ok.initOwner(BSAppUI.getStage());
						ok.showAndWait();
					});
				} catch (UnsupportedOperationException ex) {
					Platform.runLater(
							() -> showError("Import failed", "Private key import is disabled on this device."));
				} catch (Exception ex) {
					log.error("Failed to import private key", ex);
					Platform.runLater(() -> showError("Import failed", ex.getMessage()));
				}
			}, "key-import").start();
		});
	}

	private void showError(String title, String message) {
		Alert err = new Alert(Alert.AlertType.ERROR);
		err.setTitle(title);
		err.setHeaderText(title);
		err.setContentText(message);
		err.initOwner(BSAppUI.getStage());
		err.showAndWait();
	}

	boolean hasCompanion() {
		return currentCompanion != null;
	}

	ReadOnlyBooleanProperty connectedProperty() {
		return connected.getReadOnlyProperty();
	}

	MeshcoreCompanion getCompanion() {
		return currentCompanion;
	}
}
