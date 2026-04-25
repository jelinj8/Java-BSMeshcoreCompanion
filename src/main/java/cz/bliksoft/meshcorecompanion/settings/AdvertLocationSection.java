package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.SelfInfo;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class AdvertLocationSection extends VBox {

	private static final Logger log = LogManager.getLogger(AdvertLocationSection.class);

	private final TextField latField = new TextField();
	private final TextField lonField = new TextField();
	private final TextField altField = new TextField();
	private final Button readBtn = new Button("Read from device");
	private final Label titleLabel = new Label("Advert location:");

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private MeshcoreCompanion currentCompanion;
	private Runnable onModified;
	private boolean dirty = false;

	AdvertLocationSection(Runnable onModified) {
		this.onModified = onModified;

		setPadding(new Insets(0));
		setSpacing(8);

		altField.setPromptText("optional");

		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(6);

		int row = 0;
		grid.add(new Label("Latitude:"), 0, row);
		grid.add(latField, 1, row++);
		grid.add(new Label("Longitude:"), 0, row);
		grid.add(lonField, 1, row++);
		grid.add(new Label("Altitude (m):"), 0, row);
		grid.add(altField, 1, row++);

		readBtn.setOnAction(e -> readFromDevice());
		getChildren().addAll(titleLabel, grid, new HBox(readBtn));
		setDisable(true);

		wireDirtyListeners();

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "AdvertLocationSection") {
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

	private void wireDirtyListeners() {
		latField.textProperty().addListener((obs, o, n) -> markDirty());
		lonField.textProperty().addListener((obs, o, n) -> markDirty());
		altField.textProperty().addListener((obs, o, n) -> markDirty());
	}

	private void markDirty() {
		dirty = true;
		titleLabel.setText("Advert location: *");
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
				SelfInfo si = c.getConfig().getSelfInfo();
				if (si == null) {
					c.refreshSelfInfo();
					Thread.sleep(500);
					si = c.getConfig().getSelfInfo();
				}
				SelfInfo finalSi = si;
				Platform.runLater(() -> populate(finalSi));
			} catch (Exception e) {
				log.warn("Failed to read location config", e);
			} finally {
				Platform.runLater(() -> readBtn.setDisable(false));
			}
		}, "location-read").start();
	}

	private void populate(SelfInfo si) {
		if (si == null)
			return;
		Runnable saved = onModified;
		onModified = () -> {
		};

		latField.setText(String.valueOf(si.getLat()));
		lonField.setText(String.valueOf(si.getLon()));
		altField.setText("");

		onModified = saved;
		dirty = false;
		titleLabel.setText("Advert location:");
		onModified.run();
	}

	void writeToDevice() throws Exception {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;

		double lat = Double.parseDouble(latField.getText().strip());
		double lon = Double.parseDouble(lonField.getText().strip());
		String altText = altField.getText().strip();
		Integer alt = altText.isEmpty() ? null : Integer.parseInt(altText);
		c.getConfig().setAdvertLatLon(lat, lon, alt);
	}

	boolean isDirty() {
		return dirty;
	}

	void clearDirty() {
		dirty = false;
		titleLabel.setText("Advert location:");
	}

	boolean hasCompanion() {
		return currentCompanion != null;
	}

	ReadOnlyBooleanProperty connectedProperty() {
		return connected.getReadOnlyProperty();
	}

	MeshcoreCompanion getCompanion() {
		return currentCompanion;
	}
}
