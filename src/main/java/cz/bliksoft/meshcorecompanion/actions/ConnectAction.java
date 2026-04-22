package cz.bliksoft.meshcorecompanion.actions;

import cz.bliksoft.javautils.app.ui.actions.IUIAction;
import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableBooleanValue;

public class ConnectAction implements IUIAction {

    private static final ReadOnlyStringProperty CONST_TEXT = new ReadOnlyStringWrapper("Connect");

    @Override
    public void execute() {
        ConnectionManager.getInstance().openConnectDialog();
    }

    @Override
    public ObservableBooleanValue enabledProperty() {
        return ConnectionManager.getInstance().disconnectedProperty();
    }

    @Override
    public ObservableBooleanValue visibleProperty() {
        return ConnectionManager.getInstance().disconnectedProperty();
    }

    @Override
    public ReadOnlyStringProperty textProperty() {
        return CONST_TEXT;
    }

    @Override
    public String getKey() {
        return "ConnectAction";
    }
}
