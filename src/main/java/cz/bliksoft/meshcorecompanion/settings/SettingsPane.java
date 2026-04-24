package cz.bliksoft.meshcorecompanion.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import cz.bliksoft.javautils.fx.tools.Styling;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcorecompanion.events.meshcore.MeshcorePushBridge;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class SettingsPane extends VBox implements IContextProvider, IClose, ISave {

    private static final Logger log = LogManager.getLogger(SettingsPane.class);

    private static final ButtonType BTN_SAVE    = new ButtonType("Save");
    private static final ButtonType BTN_DISCARD = new ButtonType("Discard");

    private final BooleanProperty saveEnabled  = new SimpleBooleanProperty(false);
    private final BooleanProperty closeEnabled = new SimpleBooleanProperty(true);

    private String originalTheme;
    private String pendingTheme;
    private int    originalLogSize;

    private final RadioConfigSection radioSection;

    public SettingsPane() {
        setPadding(new Insets(16));
        setSpacing(12);

        Object themeObj = BSApp.getProperty(BSAppUI.PROP_THEME);
        originalTheme = themeObj != null ? capitalise(themeObj.toString()) : "Default";
        pendingTheme  = originalTheme;
        originalLogSize = MeshcorePushBridge.getInstance().getMaxLogEntries();

        radioSection = new RadioConfigSection(this::markModified);

        getChildren().addAll(
                buildAppSettingsSection(),
                new Separator(),
                radioSection,
                new Separator(),
                buildBackupSection());

        getItemContext().addValue(this);
    }

    private GridPane buildAppSettingsSection() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        int row = 0;

        grid.add(new Label("Theme:"), 0, row);
        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("Default", "System", "Light", "Dark");
        themeBox.setValue(capitalise(originalTheme));
        themeBox.setOnAction(e -> {
            pendingTheme = themeBox.getValue();
            Styling.setThemeMode(toThemeMode(pendingTheme));
            markModified();
        });
        grid.add(themeBox, 1, row++);

        grid.add(new Label("Log history size:"), 0, row);
        Spinner<Integer> logSizeSpinner = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 10000, originalLogSize, 100));
        logSizeSpinner.setEditable(true);
        logSizeSpinner.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                MeshcorePushBridge.getInstance().setMaxLogEntries(n);
                markModified();
            }
        });
        grid.add(logSizeSpinner, 1, row++);

        return grid;
    }

    private void markModified() {
        saveEnabled.set(true);
    }

    // ── ISave ────────────────────────────────────────────────────────────────

    @Override
    public void save() {
        // App settings — synchronous
        BSApp.setLocalProperty(BSAppUI.PROP_THEME, "Default".equals(pendingTheme) ? null : pendingTheme.toUpperCase());
        try {
            BSApp.saveLocalProperties();
        } catch (ViewableException e) {
            log.warn("Failed to save app settings", e);
        }
        originalTheme   = pendingTheme;
        originalLogSize = MeshcorePushBridge.getInstance().getMaxLogEntries();
        saveEnabled.set(false);

        // Radio settings — async (fire-and-forget; errors shown via log/status)
        if (radioSection.isConnected()) {
            new Thread(() -> {
                try {
                    radioSection.writeToDevice();
                } catch (Exception e) {
                    log.error("Failed to write radio config to device", e);
                    javafx.application.Platform.runLater(() -> {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.setTitle("Radio config error");
                        err.setHeaderText("Could not write radio parameters to device.");
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
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) return;
            if (result.get() == BTN_SAVE) {
                save();
            } else {
                revertApp();
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

        Button backupBtn  = new Button("Backup…");
        Button restoreBtn = new Button("Restore…");

        backupBtn.setDisable(!radioSection.isConnected());
        restoreBtn.setDisable(!radioSection.isConnected());

        radioSection.connectedProperty().addListener((obs, o, connected) -> {
            backupBtn.setDisable(!connected);
            restoreBtn.setDisable(!connected);
        });

        backupBtn.setOnAction(e -> doBackup());
        restoreBtn.setOnAction(e -> doRestore());

        HBox buttons = new HBox(8, backupBtn, restoreBtn);
        return new VBox(4, title, buttons);
    }

    private void doBackup() {
        MeshcoreCompanion c = radioSection.getCompanion();
        if (c == null) { showNoDevice(); return; }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save device backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Properties", "*.properties"));
        File file = chooser.showSaveDialog(BSAppUI.getStage());
        if (file == null) return;

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
        if (c == null) { showNoDevice(); return; }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open device backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Properties", "*.properties"));
        File file = chooser.showOpenDialog(BSAppUI.getStage());
        if (file == null) return;

        CheckBox settingsBox = new CheckBox("Device settings (radio parameters)");
        CheckBox groupsBox   = new CheckBox("Groups (channels)");
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
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        boolean restoreSettings = settingsBox.isSelected();
        boolean restoreGroups   = groupsBox.isSelected();
        boolean restoreContacts = contactsBox.isSelected();
        if (!restoreSettings && !restoreGroups && !restoreContacts) return;

        new Thread(() -> {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                if (restoreSettings) c.getConfig().deviceRestore(props);
                if (restoreGroups)   c.getConfig().channelsRestore(props);
                if (restoreContacts) c.getConfig().contactsRestore(props);
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void revertApp() {
        Styling.setThemeMode(toThemeMode(capitalise(originalTheme)));
        MeshcorePushBridge.getInstance().setMaxLogEntries(originalLogSize);
        pendingTheme = originalTheme;
    }

    private static Styling.ThemeMode toThemeMode(String label) {
        return switch (label) {
            case "Light"   -> Styling.ThemeMode.LIGHT;
            case "Dark"    -> Styling.ThemeMode.DARK;
            case "Default" -> Styling.ThemeMode.NONE;
            default        -> Styling.ThemeMode.SYSTEM;
        };
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return "System";
        String lower = s.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
