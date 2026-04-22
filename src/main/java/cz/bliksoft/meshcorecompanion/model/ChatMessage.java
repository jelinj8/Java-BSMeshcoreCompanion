package cz.bliksoft.meshcorecompanion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    private long id;
    private long timestamp;
    private String text;
    private boolean outgoing;
    private String senderName;
    private boolean confirmed;
    private String tag;

    public ChatMessage() {
    }

    public ChatMessage(long id, long timestamp, String text, boolean outgoing, String senderName, boolean confirmed, String tag) {
        this.id = id;
        this.timestamp = timestamp;
        this.text = text;
        this.outgoing = outgoing;
        this.senderName = senderName;
        this.confirmed = confirmed;
        this.tag = tag;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isOutgoing() { return outgoing; }
    public void setOutgoing(boolean outgoing) { this.outgoing = outgoing; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
}
