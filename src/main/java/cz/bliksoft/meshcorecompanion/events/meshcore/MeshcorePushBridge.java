package cz.bliksoft.meshcorecompanion.events.meshcore;

import java.time.LocalDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.FrameListener;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.ResponseFrame;
import cz.bliksoft.meshcore.frames.FrameConstants.ResponseFrameType;
import cz.bliksoft.meshcorecompanion.model.LogEntry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MeshcorePushBridge {

	private static final Logger log = LogManager.getLogger(MeshcorePushBridge.class);

	public static final int DEFAULT_MAX_LOG_ENTRIES = 1000;

	private static final MeshcorePushBridge INSTANCE = new MeshcorePushBridge();

	private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
	private int maxLogEntries = DEFAULT_MAX_LOG_ENTRIES;

	private MeshcoreCompanion currentCompanion;
	private final FrameListener<ResponseFrame> allFramesListener = this::onFrame;

	private MeshcorePushBridge() {
	}

	public static MeshcorePushBridge getInstance() {
		return INSTANCE;
	}

	public void install() {
		Context.getCurrentContext().addContextListener(
				new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "MeshcorePushBridge") {
					@Override
					public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
						onCompanionChanged(event.getNewValue());
					}
				});
	}

	private void onCompanionChanged(MeshcoreCompanion companion) {
		if (currentCompanion != null) {
			currentCompanion.removeFrameListener(allFramesListener);
			currentCompanion = null;
		}
		if (companion != null) {
			currentCompanion = companion;
			companion.registerFrameListener(ResponseFrame.class, allFramesListener);
			log.debug("MeshcorePushBridge attached to companion");
		} else {
			log.debug("MeshcorePushBridge detached");
		}
	}

	private void onFrame(ResponseFrame frame) {
		ResponseFrameType type = frame.getFrameType();
		if (type == null)
			return;

		String typeName = type.name();
		// only log PUSH_ frames in the log window
		if (!typeName.startsWith("PUSH_"))
			return;

		String summary;
		try {
			summary = frame.toString();
		} catch (Exception e) {
			summary = "<toString failed: " + e.getMessage() + ">";
		}

		LogEntry entry = new LogEntry(LocalDateTime.now(), typeName, summary);
		Platform.runLater(() -> appendLogEntry(entry));
	}

	private void appendLogEntry(LogEntry entry) {
		if (logEntries.size() >= maxLogEntries) {
			logEntries.remove(0);
		}
		logEntries.add(entry);
	}

	public ObservableList<LogEntry> getLogEntries() {
		return logEntries;
	}

	public void clear() {
		logEntries.clear();
	}

	public void addMark() {
		Platform.runLater(() -> appendLogEntry(LogEntry.marker()));
	}

	public int getMaxLogEntries() {
		return maxLogEntries;
	}

	public void setMaxLogEntries(int max) {
		this.maxLogEntries = max;
	}
}
