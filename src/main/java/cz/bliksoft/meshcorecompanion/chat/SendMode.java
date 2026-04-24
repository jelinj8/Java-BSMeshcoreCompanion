package cz.bliksoft.meshcorecompanion.chat;

public enum SendMode {
    /** Fire-and-forget: returns immediately; confirmation arrives later via push. */
    ASYNC("Async"),
    /** Synchronous: blocks until the recipient ACKs (single attempt). */
    SYNC("Sync"),
    /** Retry with flood fallback: up to 3 attempts, last one forces flood routing. */
    RETRY("Retry");

    private final String label;

    SendMode(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
