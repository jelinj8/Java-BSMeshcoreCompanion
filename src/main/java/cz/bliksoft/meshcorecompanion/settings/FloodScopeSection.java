package cz.bliksoft.meshcorecompanion.settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.DefaultFloodScope;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Regional flood-scope settings: the persisted default scope (applied on every
 * boot) and an immediate, non-persisted session override. Neither the device
 * firmware nor this UI can read back the current session override — only the
 * persisted default is queryable.
 */
class FloodScopeSection extends VBox {

	private static final Logger log = LogManager.getLogger(FloodScopeSection.class);
	private static final int SCOPE_KEY_HEX_LEN = 32; // 16 bytes

	private final Label defaultScopeStatusLabel = new Label("Default scope: unknown");
	private final TextField defaultScopeNameField = new TextField();
	private final TextField defaultScopeKeyField = new TextField();
	private final Button deriveDefaultKeyBtn = new Button("Derive from Name");
	private final Button setDefaultScopeBtn = new Button("Set Default Scope");
	private final Button clearDefaultScopeBtn = new Button("Clear Default Scope");
	private final Button refreshDefaultScopeBtn = new Button("Refresh");

	private final TextField sessionScopeNameField = new TextField();
	private final TextField sessionScopeKeyField = new TextField();
	private final Button deriveSessionKeyBtn = new Button("Derive from Name");
	private final Button applySessionScopeBtn = new Button("Apply Scope");
	private final Button clearSessionScopeBtn = new Button("Clear Override");
	private final Button forceUnscopedBtn = new Button("Force Unscoped");

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private MeshcoreCompanion currentCompanion;

	FloodScopeSection() {
		setPadding(new Insets(0));
		setSpacing(8);

		defaultScopeNameField.setPromptText("Name (max 31 chars)");
		defaultScopeNameField.setPrefColumnCount(14);
		defaultScopeKeyField.setPromptText("32-char hex key (16 bytes)");
		defaultScopeKeyField.setPrefColumnCount(28);
		sessionScopeNameField.setPromptText("Name (for deriving key)");
		sessionScopeNameField.setPrefColumnCount(14);
		sessionScopeKeyField.setPromptText("32-char hex key (16 bytes)");
		sessionScopeKeyField.setPrefColumnCount(28);

		Label deriveHint = new Label(
				"\"Derive from Name\" only works for public (\"#\") regions, where key = SHA-256(\"#\"+name). "
						+ "Private (\"$\") regions need an actual out-of-band secret key.");
		deriveHint.setWrapText(true);

		getChildren().addAll(new Label("Flood Scope (Regions):"), deriveHint,
				new Label("Persisted default scope, applied automatically on every boot:"), defaultScopeStatusLabel,
				new HBox(6, defaultScopeNameField, defaultScopeKeyField, deriveDefaultKeyBtn),
				new HBox(6, setDefaultScopeBtn, clearDefaultScopeBtn, refreshDefaultScopeBtn),
				new Label("Session override (not persisted; reverts to the default scope on reboot):"),
				new HBox(6, sessionScopeNameField, sessionScopeKeyField, deriveSessionKeyBtn),
				new HBox(6, applySessionScopeBtn, clearSessionScopeBtn, forceUnscopedBtn));

		setDisable(true);

		deriveDefaultKeyBtn.setOnAction(e -> doDeriveKey(defaultScopeNameField, defaultScopeKeyField));
		setDefaultScopeBtn.setOnAction(e -> doSetDefaultScope());
		clearDefaultScopeBtn.setOnAction(e -> doClearDefaultScope());
		refreshDefaultScopeBtn.setOnAction(e -> refreshDefaultScopeStatus());
		deriveSessionKeyBtn.setOnAction(e -> doDeriveKey(sessionScopeNameField, sessionScopeKeyField));
		applySessionScopeBtn.setOnAction(e -> doApplySessionScope());
		clearSessionScopeBtn.setOnAction(e -> doClearSessionScope());
		forceUnscopedBtn.setOnAction(e -> doForceUnscoped());

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "FloodScopeSection") {
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
			defaultScopeStatusLabel.setText("Default scope: unknown");
			return;
		}
		connected.set(true);
		setDisable(false);
		refreshDefaultScopeStatus();
	}

	private void refreshDefaultScopeStatus() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		refreshDefaultScopeBtn.setDisable(true);
		new Thread(() -> {
			try {
				DefaultFloodScope scope = c.getConfig().getDefaultFloodScope();
				Platform.runLater(() -> populateDefaultScope(scope));
			} catch (Exception ex) {
				log.warn("Failed to read default flood scope", ex);
			} finally {
				Platform.runLater(() -> refreshDefaultScopeBtn.setDisable(false));
			}
		}, "floodscope-read").start();
	}

	private void populateDefaultScope(DefaultFloodScope scope) {
		if (scope == null || !scope.hasScope()) {
			defaultScopeStatusLabel.setText("Default scope: none");
			defaultScopeNameField.clear();
			defaultScopeKeyField.clear();
		} else {
			defaultScopeStatusLabel.setText("Default scope: " + scope.getScopeName());
			defaultScopeNameField.setText(scope.getScopeName());
			defaultScopeKeyField.setText(MeshcoreUtils.hex(scope.getScopeKey()));
		}
	}

	private void doSetDefaultScope() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		String name = defaultScopeNameField.getText().strip();
		if (name.isEmpty() || name.length() > 31) {
			showError("Invalid name", "Name must be 1-31 characters.");
			return;
		}
		byte[] key = parseKey(defaultScopeKeyField.getText());
		if (key == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setDefaultFloodScope(name, key);
				Platform.runLater(this::refreshDefaultScopeStatus);
			} catch (Exception ex) {
				log.error("Failed to set default flood scope", ex);
				Platform.runLater(() -> showError("Flood scope error", ex.getMessage()));
			}
		}, "floodscope-set-default").start();
	}

	private void doClearDefaultScope() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setDefaultFloodScope(null, null);
				Platform.runLater(this::refreshDefaultScopeStatus);
			} catch (Exception ex) {
				log.error("Failed to clear default flood scope", ex);
				Platform.runLater(() -> showError("Flood scope error", ex.getMessage()));
			}
		}, "floodscope-clear-default").start();
	}

	private void doApplySessionScope() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		byte[] key = parseKey(sessionScopeKeyField.getText());
		if (key == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setFloodScope(key);
				Platform.runLater(sessionScopeKeyField::clear);
			} catch (Exception ex) {
				log.error("Failed to apply session flood scope", ex);
				Platform.runLater(() -> showError("Flood scope error", ex.getMessage()));
			}
		}, "floodscope-apply-session").start();
	}

	private void doClearSessionScope() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setFloodScope(null);
			} catch (Exception ex) {
				log.error("Failed to clear session flood scope", ex);
				Platform.runLater(() -> showError("Flood scope error", ex.getMessage()));
			}
		}, "floodscope-clear-session").start();
	}

	private void doForceUnscoped() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setFloodScopeUnscoped();
			} catch (Exception ex) {
				log.error("Failed to force unscoped flood", ex);
				Platform.runLater(() -> showError("Flood scope error", ex.getMessage()));
			}
		}, "floodscope-force-unscoped").start();
	}

	/**
	 * Fills {@code keyField} with the auto-derived key for a public ("#") region
	 * name from {@code nameField}, matching firmware's
	 * {@code TransportKeyStore::getAutoKeyFor()}: {@code SHA-256("#"+name)}
	 * truncated to the first 16 bytes. Has no effect on the device — this is a
	 * local computation the caller can review before applying/saving.
	 */
	private void doDeriveKey(TextField nameField, TextField keyField) {
		String name = nameField.getText().strip();
		if (name.isEmpty()) {
			showError("Invalid name", "Enter a region name first.");
			return;
		}
		keyField.setText(MeshcoreUtils.hex(deriveHashtagKey(name)));
	}

	private static byte[] deriveHashtagKey(String name) {
		String bare = name.startsWith("#") ? name.substring(1) : name;
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] digest = sha256.digest(("#" + bare).getBytes(StandardCharsets.UTF_8));
			return Arrays.copyOf(digest, 16);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	private byte[] parseKey(String text) {
		String hex = text.strip();
		if (hex.length() != SCOPE_KEY_HEX_LEN) {
			showError("Invalid key", "Key must be exactly 32 hex characters (16 bytes).");
			return null;
		}
		try {
			return MeshcoreUtils.fromHex(hex);
		} catch (Exception ex) {
			showError("Invalid key", "Key contains invalid hex characters.");
			return null;
		}
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
