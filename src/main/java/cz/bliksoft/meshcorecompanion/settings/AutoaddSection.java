package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.FrameConstants.AutoAddConfigFlags;
import cz.bliksoft.meshcore.frames.resp.AutoaddConfig;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class AutoaddSection extends VBox {

	private static final Logger log = LogManager.getLogger(AutoaddSection.class);

	private final CheckBox overwriteOldestBox = new CheckBox("Overwrite oldest non-favourite when full");
	private final CheckBox chatBox = new CheckBox("Chat / Companion");
	private final CheckBox repeaterBox = new CheckBox("Repeater");
	private final CheckBox roomServerBox = new CheckBox("Room server");
	private final CheckBox sensorBox = new CheckBox("Sensor");
	private final Spinner<Integer> maxHopsSpinner;

	private final Button readBtn = new Button("Read from device");
	private final Label titleLabel = new Label("Auto-add contacts:");

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private MeshcoreCompanion currentCompanion;
	private Runnable onModified;
	private boolean dirty = false;

	AutoaddSection(Runnable onModified) {
		this.onModified = onModified;

		maxHopsSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(-1, 64, -1));
		maxHopsSpinner.setEditable(true);

		setPadding(new Insets(0));
		setSpacing(8);

		readBtn.setOnAction(e -> readFromDevice());
		getChildren().addAll(titleLabel, buildGrid(), new HBox(readBtn));

		setDisable(true);
		wireDirtyListeners();

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "AutoaddSection") {
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

	private GridPane buildGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(6);

		int row = 0;
		grid.add(overwriteOldestBox, 0, row++, 2, 1);
		grid.add(new Label("Auto-add types:"), 0, row);
		VBox typeBox = new VBox(4, chatBox, repeaterBox, roomServerBox, sensorBox);
		grid.add(typeBox, 1, row++);
		grid.add(new Label("Max hops (-1 = unlimited):"), 0, row);
		grid.add(maxHopsSpinner, 1, row++);
		return grid;
	}

	private void wireDirtyListeners() {
		overwriteOldestBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		chatBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		repeaterBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		roomServerBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		sensorBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		maxHopsSpinner.valueProperty().addListener((obs, o, n) -> markDirty());
	}

	private void markDirty() {
		dirty = true;
		titleLabel.setText("Auto-add contacts: *");
		onModified.run();
	}

	private void onCompanionChanged(MeshcoreCompanion companion) {
		if (companion == null) {
			connected.set(false);
			setDisable(true);
			return;
		}
		connected.set(true);
		setDisable(false);
		readFromDevice();
	}

	private void readFromDevice() {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		readBtn.setDisable(true);
		new Thread(() -> {
			try {
				AutoaddConfig ac = c.getConfig().getAutoaddConfig();
				Platform.runLater(() -> populate(ac));
			} catch (Exception e) {
				log.warn("Failed to read auto-add config", e);
			} finally {
				Platform.runLater(() -> readBtn.setDisable(false));
			}
		}, "autoadd-read").start();
	}

	private void populate(AutoaddConfig ac) {
		if (ac == null)
			return;
		Runnable saved = onModified;
		onModified = () -> {
		};

		overwriteOldestBox.setSelected(ac.hasFlag(AutoAddConfigFlags.OVERWRITE_OLDEST));
		chatBox.setSelected(ac.hasFlag(AutoAddConfigFlags.CHAT));
		repeaterBox.setSelected(ac.hasFlag(AutoAddConfigFlags.REPEATER));
		roomServerBox.setSelected(ac.hasFlag(AutoAddConfigFlags.ROOM_SERVER));
		sensorBox.setSelected(ac.hasFlag(AutoAddConfigFlags.SENSOR));
		maxHopsSpinner.getValueFactory().setValue(ac.getAutoAddMaxHops() == 0 ? -1 : ac.getAutoAddMaxHops());

		onModified = saved;
		dirty = false;
		titleLabel.setText("Auto-add contacts:");
		onModified.run();
	}

	void writeToDevice() throws Exception {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;

		byte flags = 0;
		if (overwriteOldestBox.isSelected())
			flags |= AutoAddConfigFlags.OVERWRITE_OLDEST.mask();
		if (chatBox.isSelected())
			flags |= AutoAddConfigFlags.CHAT.mask();
		if (repeaterBox.isSelected())
			flags |= AutoAddConfigFlags.REPEATER.mask();
		if (roomServerBox.isSelected())
			flags |= AutoAddConfigFlags.ROOM_SERVER.mask();
		if (sensorBox.isSelected())
			flags |= AutoAddConfigFlags.SENSOR.mask();

		c.getConfig().setAutoAddConfig(flags, maxHopsSpinner.getValue());
	}

	boolean isDirty() {
		return dirty;
	}

	void clearDirty() {
		dirty = false;
		titleLabel.setText("Auto-add contacts:");
	}

	boolean hasCompanion() {
		return currentCompanion != null;
	}

	ReadOnlyBooleanProperty connectedProperty() {
		return connected.getReadOnlyProperty();
	}
}
