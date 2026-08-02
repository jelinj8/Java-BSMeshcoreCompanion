package cz.bliksoft.meshcorecompanion.actions;

import cz.bliksoft.javautils.app.ui.actions.IUIAction;
import cz.bliksoft.javautils.app.ui.interfaces.IIconSpecPropertyProvider;
import cz.bliksoft.javautils.fx.tools.IconspecUtils;
import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableBooleanValue;

public class ConnectAction implements IUIAction, IIconSpecPropertyProvider {

	private static final ReadOnlyStringProperty CONST_TEXT = new ReadOnlyStringWrapper("Connect");
	private final Property<String> iconSpec = new SimpleStringProperty(IconspecUtils.getIconspec("action/connect"));

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
	public Property<String> iconSpecProperty() {
		return iconSpec;
	}

	@Override
	public String getKey() {
		return "ConnectAction";
	}
}
