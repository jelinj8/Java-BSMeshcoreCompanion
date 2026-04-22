package cz.bliksoft.meshcorecompanion.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.FrameListener;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.group.MessageFrameGroup;
import cz.bliksoft.meshcore.frames.push.ContactDeletedPush;
import cz.bliksoft.meshcore.frames.push.SendConfirmedPush;
import cz.bliksoft.meshcore.frames.resp.ChannelInfo;
import cz.bliksoft.meshcore.frames.resp.ChannelMsgRecv;
import cz.bliksoft.meshcore.frames.resp.Contact;
import cz.bliksoft.meshcore.frames.resp.ContactMsgRecv;
import cz.bliksoft.meshcore.frames.resp.EndOfContacts;
import cz.bliksoft.meshcore.frames.resp.Sent;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import cz.bliksoft.meshcorecompanion.model.ChatMessage;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import static cz.bliksoft.meshcore.frames.FrameConstants.MessageTextType.TXT_TYPE_PLAIN;

public class ChatManager {

    private static final Logger log = LogManager.getLogger(ChatManager.class);
    private static final ChatManager INSTANCE = new ChatManager();

    private final ObservableList<Contact> contacts = FXCollections.observableArrayList();
    private final ObservableList<ChannelInfo> channels = FXCollections.observableArrayList();
    private final Map<String, ObservableList<ChatMessage>> loadedConversations = new HashMap<>();
    private final Map<String, IntegerProperty> unreadCounts = new HashMap<>();

    private MeshcoreCompanion currentCompanion;
    private String deviceHex;

    private FrameListener<MessageFrameGroup> messageListener;
    private FrameListener<Contact> contactListener;
    private FrameListener<ContactDeletedPush> contactDeletedListener;
    private FrameListener<EndOfContacts> endOfContactsListener;
    private FrameListener<SendConfirmedPush> sendConfirmedListener;

    private ChatManager() {
    }

    public static ChatManager getInstance() {
        return INSTANCE;
    }

    public void install() {
        Context.getCurrentContext().addContextListener(
            new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "ChatManager") {
                @Override
                public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
                    MeshcoreCompanion newVal = event.getNewValue();
                    if (newVal != null) {
                        onCompanionConnected(newVal);
                    } else {
                        onCompanionDisconnected(currentCompanion);
                    }
                }
            });
    }

    private void onCompanionConnected(MeshcoreCompanion companion) {
        this.currentCompanion = companion;

        messageListener = frame -> {
            switch (frame.getFrameType()) {
                case RESP_CONTACT_MSG_RECV:
                case RESP_CONTACT_MSG_RECV_V3: {
                    ContactMsgRecv m = (ContactMsgRecv) frame;
                    List<Contact> found = companion.getConfig().findContacts(m.getFrom6(), null);
                    Contact contact = found.isEmpty() ? null : found.get(0);
                    String senderName = contact != null ? contact.getName() : MeshcoreUtils.hex(m.getFrom6());
                    String key = contact != null ? contactKey(contact) : MeshcoreUtils.hex(m.getFrom6());
                    ChatMessage msg = new ChatMessage(0, m.getTimestamp(), m.getText(), false, senderName, true, null);
                    Platform.runLater(() -> appendIncoming(key, msg));
                    break;
                }
                case RESP_CHANNEL_MSG_RECV:
                case RESP_CHANNEL_MSG_RECV_V3: {
                    ChannelMsgRecv m = (ChannelMsgRecv) frame;
                    ChannelInfo ch = companion.getConfig().getChannel(m.getChannelIdx());
                    if (ch == null) break;
                    String key = channelKey(ch);
                    ChatMessage msg = new ChatMessage(0, m.getTimestamp(), m.getText(), false, ch.getName(), true, null);
                    Platform.runLater(() -> appendIncoming(key, msg));
                    break;
                }
                default:
                    break;
            }
        };

        contactListener = contact -> {
            if (!contact.isSaved()) return;
            Platform.runLater(() -> {
                for (int i = 0; i < contacts.size(); i++) {
                    if (MeshcoreUtils.hex(contacts.get(i).getPubkey()).equals(MeshcoreUtils.hex(contact.getPubkey()))) {
                        contacts.set(i, contact);
                        return;
                    }
                }
                contacts.add(contact);
            });
        };

        contactDeletedListener = push -> {
            String hex = MeshcoreUtils.hex(push.getPubkey());
            Platform.runLater(() -> contacts.removeIf(c -> MeshcoreUtils.hex(c.getPubkey()).equals(hex)));
        };

        endOfContactsListener = eoc -> {
            List<ChannelInfo> chs = new ArrayList<>(companion.getConfig().getChannels());
            Platform.runLater(() -> {
                channels.setAll(chs);
                if (deviceHex == null && companion.getSelfInfo() != null) {
                    deviceHex = MeshcoreUtils.hex(companion.getSelfInfo().getPubkey());
                }
            });
        };

        sendConfirmedListener = push -> {
            String tagHex = MeshcoreUtils.hex(push.getTag());
            Platform.runLater(() -> {
                for (ObservableList<ChatMessage> msgs : loadedConversations.values()) {
                    for (ChatMessage msg : msgs) {
                        if (tagHex.equals(msg.getTag())) {
                            msg.setConfirmed(true);
                            int idx = msgs.indexOf(msg);
                            if (idx >= 0) msgs.set(idx, msg);
                            return;
                        }
                    }
                }
            });
        };

        if (companion.getSelfInfo() != null) {
            deviceHex = MeshcoreUtils.hex(companion.getSelfInfo().getPubkey());
        }

        companion.registerFrameListener(MessageFrameGroup.class, messageListener);
        companion.registerFrameListener(Contact.class, contactListener);
        companion.registerFrameListener(ContactDeletedPush.class, contactDeletedListener);
        companion.registerFrameListener(EndOfContacts.class, endOfContactsListener);
        companion.registerFrameListener(SendConfirmedPush.class, sendConfirmedListener);
    }

    private void onCompanionDisconnected(MeshcoreCompanion companion) {
        if (companion != this.currentCompanion) return;
        if (messageListener != null) companion.removeFrameListener(MessageFrameGroup.class, messageListener);
        if (contactListener != null) companion.removeFrameListener(Contact.class, contactListener);
        if (contactDeletedListener != null) companion.removeFrameListener(ContactDeletedPush.class, contactDeletedListener);
        if (endOfContactsListener != null) companion.removeFrameListener(EndOfContacts.class, endOfContactsListener);
        if (sendConfirmedListener != null) companion.removeFrameListener(SendConfirmedPush.class, sendConfirmedListener);
        messageListener = null;
        contactListener = null;
        contactDeletedListener = null;
        endOfContactsListener = null;
        sendConfirmedListener = null;
        this.currentCompanion = null;
        Platform.runLater(() -> {
            contacts.clear();
            channels.clear();
        });
    }

    private void appendIncoming(String key, ChatMessage msg) {
        ObservableList<ChatMessage> msgs = getMessages(key);
        msg.setId(msgs.size());
        msgs.add(msg);
        if (deviceHex != null) ChatStore.appendMessage(deviceHex, key, msg);
        unreadCountProperty(key).set(unreadCountProperty(key).get() + 1);
    }

    public ObservableList<Contact> getContacts() {
        return contacts;
    }

    public ObservableList<ChannelInfo> getChannels() {
        return channels;
    }

    public ObservableList<ChatMessage> getMessages(String conversationKey) {
        return loadedConversations.computeIfAbsent(conversationKey, k -> {
            List<ChatMessage> stored = deviceHex != null ? ChatStore.load(deviceHex, k) : new ArrayList<>();
            return FXCollections.observableArrayList(stored);
        });
    }

    public IntegerProperty unreadCountProperty(String conversationKey) {
        return unreadCounts.computeIfAbsent(conversationKey, k -> new SimpleIntegerProperty(0));
    }

    public void markRead(String conversationKey) {
        unreadCountProperty(conversationKey).set(0);
    }

    public void sendToContact(Contact contact, String text) {
        MeshcoreCompanion c = currentCompanion;
        if (c == null) return;
        long now = Instant.now().getEpochSecond();
        String key = contactKey(contact);
        ObservableList<ChatMessage> msgs = getMessages(key);
        ChatMessage msg = new ChatMessage(msgs.size(), now, text, true, "Me", false, null);
        msgs.add(msg);
        if (deviceHex != null) ChatStore.appendMessage(deviceHex, key, msg);
        byte[] prefix6 = MeshcoreUtils.prefix6(contact.getPubkey());
        new Thread(() -> {
            try {
                Sent sent = c.sendTxtMsgAsync(TXT_TYPE_PLAIN, prefix6, 0, now, text);
                String tagHex = MeshcoreUtils.hex(sent.getAckIdOrTag());
                Platform.runLater(() -> {
                    msg.setTag(tagHex);
                    int idx = msgs.indexOf(msg);
                    if (idx >= 0) msgs.set(idx, msg);
                    if (deviceHex != null) ChatStore.save(deviceHex, key, new ArrayList<>(msgs));
                });
            } catch (Exception e) {
                log.error("Failed to send message to {}", contact.getName(), e);
            }
        }, "meshcore-send").start();
    }

    public void sendToChannel(ChannelInfo channel, String text) {
        MeshcoreCompanion c = currentCompanion;
        if (c == null) return;
        long now = Instant.now().getEpochSecond();
        String key = channelKey(channel);
        ObservableList<ChatMessage> msgs = getMessages(key);
        ChatMessage msg = new ChatMessage(msgs.size(), now, text, true, "Me", true, null);
        msgs.add(msg);
        if (deviceHex != null) ChatStore.appendMessage(deviceHex, key, msg);
        new Thread(() -> {
            try {
                c.sendChannelTxtMessage(TXT_TYPE_PLAIN, channel.getId(), now, text);
            } catch (Exception e) {
                log.error("Failed to send message to channel {}", channel.getName(), e);
            }
        }, "meshcore-send").start();
    }

    public String contactKey(Contact c) {
        return MeshcoreUtils.hex(c.getPubkey());
    }

    public String channelKey(ChannelInfo ch) {
        return "ch_" + ch.getName();
    }
}
