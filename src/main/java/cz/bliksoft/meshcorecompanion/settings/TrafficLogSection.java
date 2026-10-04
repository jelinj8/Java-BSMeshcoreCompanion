package cz.bliksoft.meshcorecompanion.settings;

import java.io.File;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcorecompanion.connection.TrafficLogging;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Traffic log switch of the connected device + transport (see
 * {@link TrafficLogging}). Applied and persisted immediately.
 */
class TrafficLogSection extends VBox {

	private final CheckBox logCheck = new CheckBox();
	private final Label hint = new Label();

	private MeshcoreCompanion currentCompanion;
	private boolean updating = false;

	TrafficLogSection() {
		setSpacing(8);
		hint.setWrapText(true);
		getChildren().addAll(new Label("Diagnostics:"), logCheck, hint);

		logCheck.selectedProperty().addListener((obs, was, now) -> {
			if (!updating)
				apply(now);
		});

		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "TrafficLogSection") {
					@Override
					public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
						MeshcoreCompanion c = event.getNewValue();
						Platform.runLater(() -> onCompanionChanged(c));
					}
				});

		var search = Context.getCurrentContext().getValue(MeshcoreCompanion.class);
		onCompanionChanged(search.isValid() && search.getResult() instanceof MeshcoreCompanion existing ? existing
				: null);
	}

	private void onCompanionChanged(MeshcoreCompanion companion) {
		currentCompanion = companion;
		File dir = TrafficLogging.dir();
		String pubkey = TrafficLogging.pubkeyOf(companion);
		String transport = companion == null ? null : TrafficLogging.transportOf(companion);

		updating = true;
		try {
			logCheck.setSelected(TrafficLogging.isEnabled(pubkey, transport));
		} finally {
			updating = false;
		}
		logCheck.setText(transport == null ? "Log device communication"
				: "Log communication of this device over " + transport.toUpperCase());
		setDisable(pubkey == null || dir == null);
		hint.setText(dir == null ? "No log directory configured."
				: "One JSON file per frame (and per BLE sidecar message) in " + dir.getAbsolutePath()
						+ ". Remembered per device and transport.");
	}

	private void apply(boolean enabled) {
		MeshcoreCompanion c = currentCompanion;
		String pubkey = TrafficLogging.pubkeyOf(c);
		if (pubkey == null)
			return;
		String transport = TrafficLogging.transportOf(c);
		TrafficLogging.setEnabled(pubkey, transport, enabled);
		c.setTrafficLogDir(TrafficLogging.dirFor(pubkey, transport));
	}
}
