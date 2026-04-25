package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.app.BSApp;
import cz.bliksoft.javautils.app.exceptions.ViewableException;
import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.fx.tools.Styling;
import cz.bliksoft.meshcorecompanion.events.meshcore.MeshcorePushBridge;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

class AppSettingsSection extends VBox {

	private static final Logger log = LogManager.getLogger(AppSettingsSection.class);

	private final Label titleLabel = new Label("Application settings:");
	private final ComboBox<String> themeBox = new ComboBox<>();
	private final Spinner<Integer> logSizeSpinner;

	private String originalTheme;
	private String pendingTheme;
	private int originalLogSize;

	private Runnable onModified;
	private boolean dirty = false;

	AppSettingsSection(Runnable onModified) {
		this.onModified = onModified;

		Object themeObj = BSApp.getProperty(BSAppUI.PROP_THEME);
		originalTheme = themeObj != null ? capitalise(themeObj.toString()) : "Default";
		pendingTheme = originalTheme;
		originalLogSize = MeshcorePushBridge.getInstance().getMaxLogEntries();

		themeBox.getItems().addAll("Default", "System", "Light", "Dark");
		themeBox.setValue(originalTheme);
		themeBox.setOnAction(e -> {
			pendingTheme = themeBox.getValue();
			Styling.setThemeMode(toThemeMode(pendingTheme));
			markDirty();
		});

		logSizeSpinner = new Spinner<>(
				new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 10000, originalLogSize, 100));
		logSizeSpinner.setEditable(true);
		logSizeSpinner.valueProperty().addListener((obs, o, n) -> {
			if (n != null) {
				MeshcorePushBridge.getInstance().setMaxLogEntries(n);
				markDirty();
			}
		});

		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(8);
		int row = 0;
		grid.add(new Label("Theme:"), 0, row);
		grid.add(themeBox, 1, row++);
		grid.add(new Label("Log history size:"), 0, row);
		grid.add(logSizeSpinner, 1, row++);

		setPadding(new Insets(0));
		setSpacing(8);
		getChildren().addAll(titleLabel, grid);
	}

	private void markDirty() {
		dirty = true;
		titleLabel.setText("Application settings: *");
		onModified.run();
	}

	void save() {
		if ("Default".equals(pendingTheme)) {
			BSApp.removeLocalProperty(BSAppUI.PROP_THEME);
		} else {
			BSApp.setLocalProperty(BSAppUI.PROP_THEME, pendingTheme.toUpperCase());
		}
		try {
			BSApp.saveLocalProperties();
		} catch (ViewableException e) {
			log.warn("Failed to save app settings", e);
		}
		originalTheme = pendingTheme;
		originalLogSize = MeshcorePushBridge.getInstance().getMaxLogEntries();
		clearDirty();
		onModified.run();
	}

	void revert() {
		Styling.setThemeMode(toThemeMode(originalTheme));
		MeshcorePushBridge.getInstance().setMaxLogEntries(originalLogSize);
		// suppress dirty notifications while reverting controls
		Runnable saved = onModified;
		onModified = () -> {
		};
		themeBox.setValue(originalTheme);
		logSizeSpinner.getValueFactory().setValue(originalLogSize);
		onModified = saved;
		pendingTheme = originalTheme;
		clearDirty();
		onModified.run();
	}

	boolean isDirty() {
		return dirty;
	}

	void clearDirty() {
		dirty = false;
		titleLabel.setText("Application settings:");
	}

	static Styling.ThemeMode toThemeMode(String label) {
		return switch (label) {
		case "Light" -> Styling.ThemeMode.LIGHT;
		case "Dark" -> Styling.ThemeMode.DARK;
		case "Default" -> Styling.ThemeMode.NONE;
		default -> Styling.ThemeMode.SYSTEM;
		};
	}

	private static String capitalise(String s) {
		if (s == null || s.isEmpty())
			return "System";
		String lower = s.toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
