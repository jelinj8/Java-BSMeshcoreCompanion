package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.DeviceInfo;
import cz.bliksoft.meshcore.frames.resp.TuningParams;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class TuningSection extends VBox {

	private static final Logger log = LogManager.getLogger(TuningSection.class);

	private static final String[] PATH_HASH_LABELS = { "1 hash/hop (min)", "2 hashes/hop", "3 hashes/hop (max)" };

	private final TextField rxDelayBaseField = new TextField();
	private final TextField airtimeFactorField = new TextField();
	private final ChoiceBox<String> pathHashModeBox = new ChoiceBox<>();
	private final Label pathHashNote = new Label("(firmware v1.14+ only)");

	private final Button readBtn = new Button("Read from device");
	private final Label titleLabel = new Label("Tuning parameters:");

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private MeshcoreCompanion currentCompanion;
	private Runnable onModified;
	private boolean dirty = false;
	private boolean pathHashSupported = false;

	TuningSection(Runnable onModified) {
		this.onModified = onModified;

		pathHashModeBox.getItems().addAll(PATH_HASH_LABELS);

		setPadding(new Insets(0));
		setSpacing(8);

		GridPane grid = buildGrid();
		readBtn.setOnAction(e -> readFromDevice());
		getChildren().addAll(titleLabel, grid, new HBox(readBtn));
		setDisable(true);

		wireDirtyListeners();

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "TuningSection") {
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
		grid.add(new Label("RX delay base (ms):"), 0, row);
		grid.add(rxDelayBaseField, 1, row++);

		grid.add(new Label("Airtime factor:"), 0, row);
		grid.add(airtimeFactorField, 1, row++);

		grid.add(new Label("Path hash mode:"), 0, row);
		grid.add(pathHashModeBox, 1, row);
		grid.add(pathHashNote, 2, row++);

		return grid;
	}

	private void wireDirtyListeners() {
		rxDelayBaseField.textProperty().addListener((obs, o, n) -> markDirty());
		airtimeFactorField.textProperty().addListener((obs, o, n) -> markDirty());
		pathHashModeBox.valueProperty().addListener((obs, o, n) -> markDirty());
	}

	private void markDirty() {
		dirty = true;
		titleLabel.setText("Tuning parameters: *");
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
				TuningParams tp = c.getConfig().getTuningParams();
				DeviceInfo di = c.getConfig().getDeviceInfo();
				Platform.runLater(() -> populate(tp, di));
			} catch (Exception e) {
				log.warn("Failed to read tuning params", e);
			} finally {
				Platform.runLater(() -> readBtn.setDisable(false));
			}
		}, "tuning-read").start();
	}

	private void populate(TuningParams tp, DeviceInfo di) {
		Runnable saved = onModified;
		onModified = () -> {
		};

		if (tp != null) {
			rxDelayBaseField.setText(String.valueOf(tp.getRxDelayBase()));
			airtimeFactorField.setText(String.valueOf(tp.getAirtimeFactor()));
		}

		pathHashSupported = di != null && di.getProtocolVersion() >= 10;
		pathHashModeBox.setDisable(!pathHashSupported);
		pathHashNote.setVisible(!pathHashSupported);

		if (pathHashSupported && di != null) {
			int mode = di.getPathHashMode();
			if (mode >= 0 && mode < PATH_HASH_LABELS.length) {
				pathHashModeBox.setValue(PATH_HASH_LABELS[mode]);
			}
		}

		onModified = saved;
		dirty = false;
		titleLabel.setText("Tuning parameters:");
		onModified.run();
	}

	void writeToDevice() throws Exception {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;

		double rxDelay = Double.parseDouble(rxDelayBaseField.getText().strip());
		double airtime = Double.parseDouble(airtimeFactorField.getText().strip());
		c.getConfig().setTuningParams(rxDelay, airtime);

		if (pathHashSupported && pathHashModeBox.getValue() != null) {
			int modeIdx = pathHashModeBox.getItems().indexOf(pathHashModeBox.getValue());
			if (modeIdx >= 0) {
				c.getConfig().setPathHashMode((byte) modeIdx);
			}
		}
	}

	boolean isDirty() {
		return dirty;
	}

	void clearDirty() {
		dirty = false;
		titleLabel.setText("Tuning parameters:");
	}

	boolean hasCompanion() {
		return currentCompanion != null;
	}

	ReadOnlyBooleanProperty connectedProperty() {
		return connected.getReadOnlyProperty();
	}
}
