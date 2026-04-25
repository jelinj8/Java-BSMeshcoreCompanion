package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.FrameConstants.AdvertLocPolicy;
import cz.bliksoft.meshcore.frames.resp.SelfInfo;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class ContactBehaviourSection extends VBox {

	private static final Logger log = LogManager.getLogger(ContactBehaviourSection.class);

	private static final String[] LOC_POLICY_LABELS = { "Don't share", "Always share", "Per preferences" };
	private static final AdvertLocPolicy[] LOC_POLICY_VALUES = { AdvertLocPolicy.ADVERT_LOC_NONE,
			AdvertLocPolicy.ADVERT_LOC_SHARE, AdvertLocPolicy.ADVERT_LOC_PREFS };

	private final CheckBox manualAddContactsBox = new CheckBox("Manual contact management");
	private final ChoiceBox<String> advertLocPolicyBox = new ChoiceBox<>();
	private final CheckBox multiAcksBox = new CheckBox("Multi-ACKs");

	private final CheckBox telemetryBaseAllBox = new CheckBox("All contacts");
	private final CheckBox telemetryBaseFavBox = new CheckBox("Favourites only");
	private final CheckBox telemetryLocAllBox = new CheckBox("All contacts");
	private final CheckBox telemetryLocFavBox = new CheckBox("Favourites only");
	private final CheckBox telemetryEnvAllBox = new CheckBox("All contacts");
	private final CheckBox telemetryEnvFavBox = new CheckBox("Favourites only");

	private final Button readBtn = new Button("Read from device");
	private final Label titleLabel = new Label("Contact behaviour:");

	private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);
	private MeshcoreCompanion currentCompanion;
	private Runnable onModified;
	private boolean dirty = false;

	ContactBehaviourSection(Runnable onModified) {
		this.onModified = onModified;

		advertLocPolicyBox.getItems().addAll(LOC_POLICY_LABELS);

		setPadding(new Insets(0));
		setSpacing(8);

		readBtn.setOnAction(e -> readFromDevice());
		getChildren().addAll(titleLabel, buildOtherParamsGrid(), new Label("Telemetry sharing:"), buildTelemetryGrid(),
				new HBox(readBtn));

		setDisable(true);
		wireDirtyListeners();

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "ContactBehaviourSection") {
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

	private GridPane buildOtherParamsGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(6);

		int row = 0;
		grid.add(manualAddContactsBox, 0, row++, 2, 1);
		grid.add(multiAcksBox, 0, row++, 2, 1);
		grid.add(new Label("Location sharing:"), 0, row);
		grid.add(advertLocPolicyBox, 1, row++);
		return grid;
	}

	private GridPane buildTelemetryGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(4);

		int row = 0;
		grid.add(new Label("Base telemetry:"), 0, row);
		grid.add(telemetryBaseAllBox, 1, row);
		grid.add(telemetryBaseFavBox, 2, row++);

		grid.add(new Label("Location telemetry:"), 0, row);
		grid.add(telemetryLocAllBox, 1, row);
		grid.add(telemetryLocFavBox, 2, row++);

		grid.add(new Label("Environment telemetry:"), 0, row);
		grid.add(telemetryEnvAllBox, 1, row);
		grid.add(telemetryEnvFavBox, 2, row++);

		return grid;
	}

	private void wireDirtyListeners() {
		manualAddContactsBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		multiAcksBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		advertLocPolicyBox.valueProperty().addListener((obs, o, n) -> markDirty());
		telemetryBaseAllBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		telemetryBaseFavBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		telemetryLocAllBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		telemetryLocFavBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		telemetryEnvAllBox.selectedProperty().addListener((obs, o, n) -> markDirty());
		telemetryEnvFavBox.selectedProperty().addListener((obs, o, n) -> markDirty());
	}

	private void markDirty() {
		dirty = true;
		titleLabel.setText("Contact behaviour: *");
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
				log.warn("Failed to read contact behaviour config", e);
			} finally {
				Platform.runLater(() -> readBtn.setDisable(false));
			}
		}, "behaviour-read").start();
	}

	private void populate(SelfInfo si) {
		if (si == null)
			return;
		Runnable saved = onModified;
		onModified = () -> {
		};

		manualAddContactsBox.setSelected(si.isManualAddContacts());
		multiAcksBox.setSelected(si.getMultiAcks());

		AdvertLocPolicy policy = si.getAdvertLocPolicy();
		for (int i = 0; i < LOC_POLICY_VALUES.length; i++) {
			if (LOC_POLICY_VALUES[i] == policy) {
				advertLocPolicyBox.setValue(LOC_POLICY_LABELS[i]);
				break;
			}
		}

		telemetryBaseAllBox.setSelected(si.isTelemetryModeBaseEn());
		telemetryBaseFavBox.setSelected(si.isTelemetryModeBaseFav());
		telemetryLocAllBox.setSelected(si.isTelemetryModeLocEn());
		telemetryLocFavBox.setSelected(si.isTelemetryModeLocFav());
		telemetryEnvAllBox.setSelected(si.isTelemetryModeEnvEn());
		telemetryEnvFavBox.setSelected(si.isTelemetryModeEnvFav());

		onModified = saved;
		dirty = false;
		titleLabel.setText("Contact behaviour:");
		onModified.run();
	}

	void writeToDevice() throws Exception {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;

		int policyIdx = advertLocPolicyBox.getItems().indexOf(advertLocPolicyBox.getValue());
		AdvertLocPolicy policy = policyIdx >= 0 ? LOC_POLICY_VALUES[policyIdx] : AdvertLocPolicy.ADVERT_LOC_NONE;

		c.getConfig().setOtherParams(manualAddContactsBox.isSelected(), telemetryBaseAllBox.isSelected(),
				telemetryBaseFavBox.isSelected(), telemetryLocAllBox.isSelected(), telemetryLocFavBox.isSelected(),
				telemetryEnvAllBox.isSelected(), telemetryEnvFavBox.isSelected(), policy, multiAcksBox.isSelected());
	}

	boolean isDirty() {
		return dirty;
	}

	void clearDirty() {
		dirty = false;
		titleLabel.setText("Contact behaviour:");
	}

	boolean hasCompanion() {
		return currentCompanion != null;
	}

	ReadOnlyBooleanProperty connectedProperty() {
		return connected.getReadOnlyProperty();
	}
}
