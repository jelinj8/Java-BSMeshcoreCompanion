package cz.bliksoft.meshcorecompanion.actions;

import cz.bliksoft.javautils.app.ui.actions.IUIAction;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableBooleanValue;

public class OpenSettingsAction implements IUIAction {

    private static final ReadOnlyStringProperty CONST_TEXT = new ReadOnlyStringWrapper("Settings");
    private static final ReadOnlyBooleanProperty CONST_ENABLED = new ReadOnlyBooleanWrapper(true);

    @Override
    public void execute() {
        // TODO: push settings pane via BSAppUI.pushUI()
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
    public String getKey() {
        return "OpenSettingsAction";
    }
}
