package cz.bliksoft.meshcorecompanion.actions;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.app.ui.actions.IUIAction;
import cz.bliksoft.javautils.app.ui.interfaces.IIconSpecPropertyProvider;
import cz.bliksoft.javautils.fx.tools.IconspecUtils;
import cz.bliksoft.meshcorecompanion.settings.SettingsPane;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableBooleanValue;

public class OpenSettingsAction implements IUIAction, IIconSpecPropertyProvider {

	private static final ReadOnlyStringProperty CONST_TEXT = new ReadOnlyStringWrapper("Settings");
	private static final ReadOnlyBooleanProperty CONST_ENABLED = new ReadOnlyBooleanWrapper(true);
	// resolved from /core/iconspec/action/settings (registered in
	// BSMeshcoreCompanionModule.xml),
	// using the same ${toolbar-size} scaling token as BSToolbox-jfx's own default
	// actions
	private final Property<String> iconSpec = new SimpleStringProperty(IconspecUtils.getIconspec("action/settings"));

	@Override
	public void execute() {
		BSAppUI.pushUI(new SettingsPane());
	}

	@Override
	public ObservableBooleanValue enabledProperty() {
		return CONST_ENABLED;
	}

	@Override
	public ReadOnlyStringProperty textProperty() {
		return CONST_TEXT;
	}

	@Override
	public Property<String> iconSpecProperty() {
		return iconSpec;
	}

	@Override
	public String getKey() {
		return "OpenSettingsAction";
	}
}
