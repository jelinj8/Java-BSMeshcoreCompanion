package cz.bliksoft.meshcorecompanion.actions;

import cz.bliksoft.javautils.app.ui.actions.IUIAction;
import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableBooleanValue;

public class DisconnectAction implements IUIAction {

    private static final ReadOnlyStringProperty CONST_TEXT = new ReadOnlyStringWrapper("Disconnect");

    @Override
    public void execute() {
        ConnectionManager.getInstance().disconnect();
    }

    @Override
    public ObservableBooleanValue enabledProperty() {
        return ConnectionManager.getInstance().connectedProperty();
    }

    @Override
    public ObservableBooleanValue visibleProperty() {
        return ConnectionManager.getInstance().connectedProperty();
    }

    @Override
    public ReadOnlyStringProperty textProperty() {
        return CONST_TEXT;
    }

    @Override
    public String getKey() {
        return "DisconnectAction";
    }
}
