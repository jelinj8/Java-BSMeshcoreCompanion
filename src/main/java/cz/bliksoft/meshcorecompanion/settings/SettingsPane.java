package cz.bliksoft.meshcorecompanion.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.app.BSApp;
import cz.bliksoft.javautils.app.exceptions.ViewableException;
import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.app.ui.actions.interfaces.IClose;
import cz.bliksoft.javautils.app.ui.actions.interfaces.ISave;
import cz.bliksoft.javautils.context.IContextProvider;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.SelfInfo;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class SettingsPane extends VBox implements IContextProvider, IClose, ISave {

	private static final Logger log = LogManager.getLogger(SettingsPane.class);

	private static final ButtonType BTN_SAVE = new ButtonType("Save");
	private static final ButtonType BTN_DISCARD = new ButtonType("Discard");

	private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
	private final BooleanProperty closeEnabled = new SimpleBooleanProperty(true);

	private final AppSettingsSection appSection;
	private final RadioConfigSection radioSection;
	private final AdvertLocationSection locationSection;
	private final ContactBehaviourSection behaviourSection;
	private final AutoaddSection autoaddSection;
	private final TuningSection tuningSection;
	private final SecuritySection securitySection;

	public SettingsPane() {
		appSection = new AppSettingsSection(this::markModified);
		radioSection = new RadioConfigSection(this::markModified);
		locationSection = new AdvertLocationSection(this::markModified);
		behaviourSection = new ContactBehaviourSection(this::markModified);
		autoaddSection = new AutoaddSection(this::markModified);
		tuningSection = new TuningSection(this::markModified);
		securitySection = new SecuritySection();

		VBox content = new VBox(12, appSection, new Separator(), radioSection, new Separator(), locationSection,
				new Separator(), behaviourSection, new Separator(), autoaddSection, new Separator(), tuningSection,
				new Separator(), securitySection, new Separator(), buildBackupSection(), new Separator(),
				buildDangerZoneSection());
		content.setPadding(new Insets(16));

		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

		getChildren().add(scroll);
		setFillWidth(true);

		getItemContext().addValue(this);
	}

	private void markModified() {
		boolean anyDirty = appSection.isDirty() || radioSection.isDirty() || locationSection.isDirty()
				|| behaviourSection.isDirty() || autoaddSection.isDirty() || tuningSection.isDirty();
		saveEnabled.set(anyDirty);
	}

	// ── ISave ────────────────────────────────────────────────────────────────

	@Override
	public void save() {
		if (appSection.isDirty()) {
			appSection.save();
		}
		saveEnabled.set(false);

		// Device settings — async, only modified sections
		if (radioSection.hasCompanion() && (radioSection.isDirty() || locationSection.isDirty()
				|| behaviourSection.isDirty() || autoaddSection.isDirty() || tuningSection.isDirty())) {
			new Thread(() -> {
				try {
					if (radioSection.isDirty()) {
						radioSection.writeToDevice();
						javafx.application.Platform.runLater(radioSection::clearDirty);
					}
					if (locationSection.isDirty()) {
						locationSection.writeToDevice();
						javafx.application.Platform.runLater(locationSection::clearDirty);
					}
					if (behaviourSection.isDirty()) {
						behaviourSection.writeToDevice();
						javafx.application.Platform.runLater(behaviourSection::clearDirty);
					}
					if (autoaddSection.isDirty()) {
						autoaddSection.writeToDevice();
						javafx.application.Platform.runLater(autoaddSection::clearDirty);
					}
					if (tuningSection.isDirty()) {
						tuningSection.writeToDevice();
						javafx.application.Platform.runLater(tuningSection::clearDirty);
					}
				} catch (Exception e) {
					log.error("Failed to write device config", e);
					javafx.application.Platform.runLater(() -> {
						Alert err = new Alert(Alert.AlertType.ERROR);
						err.setTitle("Device config error");
						err.setHeaderText("Could not write parameters to device.");
						err.setContentText(e.getMessage());
						err.initOwner(BSAppUI.getStage());
						err.showAndWait();
					});
				}
			}, "radio-write").start();
		}
	}

	@Override
	public BooleanProperty getSaveEnabled() {
		return saveEnabled;
	}

	// ── IClose ───────────────────────────────────────────────────────────────

	@Override
	public void close() {
		if (saveEnabled.get()) {
			Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
			alert.setTitle("Unsaved changes");
			alert.setHeaderText("Settings have unsaved changes.");
			alert.setContentText("Do you want to save before closing?");
			alert.initOwner(BSAppUI.getStage());
			alert.getButtonTypes().setAll(BTN_SAVE, BTN_DISCARD, ButtonType.CANCEL);

			Optional<ButtonType> result = alert.showAndWait();
			if (result.isEmpty() || result.get() == ButtonType.CANCEL)
				return;
			if (result.get() == BTN_SAVE) {
				save();
			} else {
				appSection.revert();
			}
		}
		BSAppUI.popUI();
	}

	@Override
	public BooleanProperty getCloseEnabled() {
		return closeEnabled;
	}

	// ── Backup / Restore ─────────────────────────────────────────────────────

	private VBox buildBackupSection() {
		Label title = new Label("Device Backup / Restore");
		title.setStyle("-fx-font-weight: bold;");

		Button backupBtn = new Button("Backup…");
		Button restoreBtn = new Button("Restore…");

		backupBtn.setDisable(!radioSection.hasCompanion());
		restoreBtn.setDisable(!radioSection.hasCompanion());

		radioSection.connectedProperty().addListener((obs, o, connected) -> {
			backupBtn.setDisable(!connected);
			restoreBtn.setDisable(!connected);
		});

		backupBtn.setOnAction(e -> doBackup());
		restoreBtn.setOnAction(e -> doRestore());

		HBox buttons = new HBox(8, backupBtn, restoreBtn);
		return new VBox(4, title, buttons);
	}

	private static final String PROP_BACKUP_LAST_DIR = "backup.lastDir";

	private void doBackup() {
		MeshcoreCompanion c = radioSection.getCompanion();
		if (c == null) {
			showNoDevice();
			return;
		}

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save device backup");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Properties", "*.properties"));

		String lastDir = (String) BSApp.getProperty(PROP_BACKUP_LAST_DIR);
		if (lastDir != null) {
			File dir = new File(lastDir);
			if (dir.isDirectory())
				chooser.setInitialDirectory(dir);
		}

		SelfInfo si = c.getConfig().getSelfInfo();
		String nodeName = (si != null && si.getNodeName() != null && !si.getNodeName().isBlank())
				? si.getNodeName().strip().replaceAll("[^A-Za-z0-9._-]", "_")
				: "backup";
		String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		chooser.setInitialFileName(nodeName + "_" + timestamp + ".properties");

		File file = chooser.showSaveDialog(BSAppUI.getStage());
		if (file == null)
			return;

		BSApp.setLocalProperty(PROP_BACKUP_LAST_DIR, file.getParent());
		try {
			BSApp.saveLocalProperties();
		} catch (ViewableException e) {
			log.warn("Could not save backup dir preference", e);
		}

		new Thread(() -> {
			Properties props = new Properties();
			try {
				c.getConfig().deviceBackup(props);
				c.getConfig().channelsBackup(props);
				c.getConfig().contactsBackup(props);
				try (FileOutputStream out = new FileOutputStream(file)) {
					props.store(out, "BSMeshcoreCompanion device backup");
				}
				javafx.application.Platform.runLater(() -> {
					Alert info = new Alert(Alert.AlertType.INFORMATION);
					info.setTitle("Backup complete");
					info.setHeaderText("Device backup saved successfully.");
					info.initOwner(BSAppUI.getStage());
					info.showAndWait();
				});
			} catch (Exception ex) {
				log.error("Backup failed", ex);
				javafx.application.Platform.runLater(() -> showError("Backup failed", ex.getMessage()));
			}
		}, "meshcore-backup").start();
	}

	private void doRestore() {
		MeshcoreCompanion c = radioSection.getCompanion();
		if (c == null) {
			showNoDevice();
			return;
		}

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Open device backup");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Properties", "*.properties"));

		String lastDir = (String) BSApp.getProperty(PROP_BACKUP_LAST_DIR);
		if (lastDir != null) {
			File dir = new File(lastDir);
			if (dir.isDirectory())
				chooser.setInitialDirectory(dir);
		}

		File file = chooser.showOpenDialog(BSAppUI.getStage());
		if (file == null)
			return;

		BSApp.setLocalProperty(PROP_BACKUP_LAST_DIR, file.getParent());
		try {
			BSApp.saveLocalProperties();
		} catch (ViewableException e) {
			log.warn("Could not save backup dir preference", e);
		}

		CheckBox settingsBox = new CheckBox("Device settings (radio parameters)");
		CheckBox groupsBox = new CheckBox("Groups (channels)");
		CheckBox contactsBox = new CheckBox("Contacts");
		settingsBox.setSelected(true);
		groupsBox.setSelected(true);
		contactsBox.setSelected(true);

		VBox selection = new VBox(6, new Label("Select what to restore:"), settingsBox, groupsBox, contactsBox);
		selection.setPadding(new Insets(8, 0, 0, 0));

		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Restore backup");
		confirm.setHeaderText("Restore device configuration from file?");
		confirm.setContentText("Only the selected parts will be restored.");
		confirm.getDialogPane().setExpandableContent(selection);
		confirm.getDialogPane().setExpanded(true);
		confirm.initOwner(BSAppUI.getStage());
		Optional<ButtonType> result = confirm.showAndWait();
		if (result.isEmpty() || result.get() != ButtonType.OK)
			return;

		boolean restoreSettings = settingsBox.isSelected();
		boolean restoreGroups = groupsBox.isSelected();
		boolean restoreContacts = contactsBox.isSelected();
		if (!restoreSettings && !restoreGroups && !restoreContacts)
			return;

		new Thread(() -> {
			Properties props = new Properties();
			try (FileInputStream in = new FileInputStream(file)) {
				props.load(in);
				if (restoreSettings)
					c.getConfig().deviceRestore(props);
				if (restoreGroups)
					c.getConfig().channelsRestore(props);
				if (restoreContacts)
					c.getConfig().contactsRestore(props);
				javafx.application.Platform.runLater(() -> {
					Alert info = new Alert(Alert.AlertType.INFORMATION);
					info.setTitle("Restore complete");
					info.setHeaderText("Device configuration restored successfully.");
					info.initOwner(BSAppUI.getStage());
					info.showAndWait();
				});
			} catch (Exception ex) {
				log.error("Restore failed", ex);
				javafx.application.Platform.runLater(() -> showError("Restore failed", ex.getMessage()));
			}
		}, "meshcore-restore").start();
	}

	// ── Danger zone ──────────────────────────────────────────────────────────

	private VBox buildDangerZoneSection() {
		Label title = new Label("Danger Zone");
		title.setStyle("-fx-font-weight: bold; -fx-text-fill: #cc0000;");

		Button resetBtn = new Button("Factory Reset…");
		resetBtn.setStyle("-fx-background-color: #cc0000; -fx-text-fill: white;");

		resetBtn.setDisable(!radioSection.hasCompanion());
		radioSection.connectedProperty().addListener((obs, o, connected) -> resetBtn.setDisable(!connected));

		resetBtn.setOnAction(e -> doFactoryReset());

		return new VBox(4, title, resetBtn);
	}

	private void doFactoryReset() {
		MeshcoreCompanion c = radioSection.getCompanion();
		if (c == null) {
			showNoDevice();
			return;
		}

		Alert confirm = new Alert(Alert.AlertType.WARNING);
		confirm.setTitle("Factory Reset");
		confirm.setHeaderText("This will erase all device configuration!");
		confirm.setContentText("All radio settings, contacts, groups and custom variables will be lost.\n"
				+ "The device will return to factory defaults.\n\n" + "This cannot be undone. Continue?");
		confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		confirm.initOwner(BSAppUI.getStage());

		confirm.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK)
				return;
			new Thread(() -> {
				c.factoryReset();
				javafx.application.Platform.runLater(() -> {
					Alert info = new Alert(Alert.AlertType.INFORMATION);
					info.setTitle("Factory reset complete");
					info.setHeaderText("Device has been reset to factory defaults.");
					info.initOwner(BSAppUI.getStage());
					info.showAndWait();
				});
			}, "factory-reset").start();
		});
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private void showNoDevice() {
		Alert err = new Alert(Alert.AlertType.WARNING);
		err.setTitle("Not connected");
		err.setHeaderText("No device connected.");
		err.initOwner(BSAppUI.getStage());
		err.showAndWait();
	}

	private void showError(String title, String message) {
		Alert err = new Alert(Alert.AlertType.ERROR);
		err.setTitle(title);
		err.setHeaderText(title);
		err.setContentText(message);
		err.initOwner(BSAppUI.getStage());
		err.showAndWait();
	}
}
