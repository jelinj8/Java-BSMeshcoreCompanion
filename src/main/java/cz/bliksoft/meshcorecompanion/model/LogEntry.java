package cz.bliksoft.meshcorecompanion.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LogEntry {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	public static final String MARKER_TYPE = "MARK";

	private final LocalDateTime timestamp;
	private final String frameType;
	private final String summary;

	public LogEntry(LocalDateTime timestamp, String frameType, String summary) {
		this.timestamp = timestamp;
		this.frameType = frameType;
		this.summary = summary;
	}

	public static LogEntry marker() {
		return new LogEntry(LocalDateTime.now(), MARKER_TYPE, "");
	}

	public boolean isMarker() {
		return MARKER_TYPE.equals(frameType);
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public String getFrameType() {
		return frameType;
	}

	public String getSummary() {
		return summary;
	}

	public String getFormattedTimestamp() {
		return timestamp.format(FMT);
	}

	@Override
	public String toString() {
		if (isMarker()) {
			return "──────────────── " + getFormattedTimestamp() + " ────────────────";
		}
		return "[" + getFormattedTimestamp() + "] " + frameType + "  " + summary;
	}
}
