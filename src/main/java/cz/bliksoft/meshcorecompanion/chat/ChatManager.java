package cz.bliksoft.meshcorecompanion.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.FrameListener;
import cz.bliksoft.meshcore.Settings;
import cz.bliksoft.meshcore.companion.ContactListener;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.FrameConstants.AdvertType;
import cz.bliksoft.meshcore.frames.FrameConstants.ContactFlags;
import cz.bliksoft.meshcore.frames.FrameConstants.MessageTextType;
import cz.bliksoft.meshcore.frames.cmd.CmdAddUpdateContact;
import cz.bliksoft.meshcore.frames.cmd.CmdGetContactByKey;
import cz.bliksoft.meshcore.frames.group.MessageFrameGroup;
import cz.bliksoft.meshcore.frames.push.LogRXDataPush;
import cz.bliksoft.meshcore.frames.push.NewAdvertPush;
import cz.bliksoft.meshcore.frames.push.SendConfirmedPush;
import cz.bliksoft.meshcore.frames.resp.ChannelInfo;
import cz.bliksoft.meshcore.frames.resp.ChannelMsgRecv;
import cz.bliksoft.meshcore.frames.resp.Contact;
import cz.bliksoft.meshcore.frames.resp.ContactMsgRecv;
import cz.bliksoft.meshcore.frames.resp.Error;
import cz.bliksoft.meshcore.frames.resp.Sent;
import cz.bliksoft.meshcore.otaframe.OtaFrame;
import cz.bliksoft.meshcore.otaframe.OtaGroupFrame;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import cz.bliksoft.meshcorecompanion.model.ChatMessage;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ChatManager {

	private static final Logger log = LogManager.getLogger(ChatManager.class);
	private static final ChatManager INSTANCE = new ChatManager();

	private final ObservableList<Contact> contacts = FXCollections.observableArrayList();
	private final ObservableList<NewAdvertPush> discoveredContacts = FXCollections.observableArrayList();
	private final ObservableList<ChannelInfo> channels = FXCollections.observableArrayList();
	private final Map<String, ObservableList<ChatMessage>> loadedConversations = new HashMap<>();
	private final Map<String, IntegerProperty> unreadCounts = new HashMap<>();
	private final IntegerProperty totalContactUnread = new SimpleIntegerProperty(0);
	private final IntegerProperty totalGroupUnread = new SimpleIntegerProperty(0);
	private String activeConversationKey = null;
	private boolean conversationAtBottom = false;
	private final Set<String> authenticatedContacts = new HashSet<>();
	private final Map<String, Long> lastRoomTimestamp = new ConcurrentHashMap<>();

	private volatile Thread keepAliveThread;
	private volatile Contact keepAliveContact;

	private MeshcoreCompanion currentCompanion;
	private String deviceHex;
	private Runnable onAuthChanged;

	private FrameListener<MessageFrameGroup> messageListener;
	private ContactListener contactListener;
	private FrameListener<SendConfirmedPush> sendConfirmedListener;
	private FrameListener<NewAdvertPush> newAdvertListener;

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
						if (newVal != null)
							onCompanionConnected(newVal);
						else
							onCompanionDisconnected(currentCompanion);
					}
				});
	}

	public void setOnAuthChanged(Runnable callback) {
		this.onAuthChanged = callback;
	}

	// ── Connection lifecycle ─────────────────────────────────────────────────

	private void onCompanionConnected(MeshcoreCompanion companion) {
		this.currentCompanion = companion;
		companion.setLogFramePairing(true);

		// Set device clock only when it differs from system time by more than 2 hours
		// (covers DST; avoids firmware rejecting a timestamp older than its current
		// time).
		new Thread(() -> {
			try {
				if (companion != currentCompanion)
					return;
				long deviceTime = companion.getConfig().getDeviceTime().getTimestamp();
				if (companion != currentCompanion)
					return;
				long systemTime = Instant.now().getEpochSecond();
				long diff = Math.abs(systemTime - deviceTime);
				if (diff > 7200) {
					companion.getConfig().setDeviceTime(null);
					log.info("Device time set (was off by {} s)", diff);
				} else {
					log.debug("Device time within 2 h of system time, skipping ({} s off)", diff);
				}
			} catch (IllegalStateException e) {
				log.debug("Device time check aborted — disconnected mid-flight");
			} catch (Exception e) {
				log.warn("Failed to check/set device time", e);
			}
		}, "meshcore-settime").start();

		if (companion.getSelfInfo() != null) {
			deviceHex = MeshcoreUtils.hex(companion.getSelfInfo().getPubkey());
		}

		messageListener = frame -> {
			switch (frame.getFrameType()) {
			case RESP_CONTACT_MSG_RECV, RESP_CONTACT_MSG_RECV_V3 -> {
				ContactMsgRecv m = (ContactMsgRecv) frame;
				List<Contact> found = companion.getConfig().findContacts(m.getFrom6(), null);
				Contact contact = found.isEmpty() ? null : found.get(0);
				String key = contact != null ? contactKey(contact) : MeshcoreUtils.hex(m.getFrom6());
				// TXT_TYPE_SIGNED_PLAIN: room server forwarded a post from another member;
				// the actual author's 4-byte prefix is in getSenderPrefix(), not from6.
				String senderName;
				byte[] authorPrefix = m.getSenderPrefix();
				if (authorPrefix != null && authorPrefix.length > 0) {
					List<Contact> authors = companion.getConfig().findContacts(authorPrefix, null);
					senderName = authors.isEmpty() ? MeshcoreUtils.hex(authorPrefix) : authors.get(0).getName();
				} else {
					senderName = contact != null ? contact.getName() : MeshcoreUtils.hex(m.getFrom6());
				}
				ChatMessage msg = new ChatMessage(0, m.getTimestamp(), m.getText(), false, senderName, true, null);
				if (m.getTextType() != null)
					msg.setTxtType(m.getTextType().name());
				msg.setSenderHex(authorPrefix != null && authorPrefix.length > 0 ? MeshcoreUtils.hex(authorPrefix)
						: MeshcoreUtils.hex(m.getFrom6()));
				setSignalInfo(msg, frame);
				// Track latest post timestamp per contact for room keep-alive sync_since
				lastRoomTimestamp.merge(key, m.getTimestamp(), Math::max);
				Platform.runLater(() -> appendIncoming(key, msg));
			}
			case RESP_CHANNEL_MSG_RECV, RESP_CHANNEL_MSG_RECV_V3 -> {
				ChannelMsgRecv m = (ChannelMsgRecv) frame;
				ChannelInfo ch = companion.getConfig().getChannel(m.getChannelIdx());
				if (ch == null)
					break;
				String key = channelKey(ch);
				String rawText = m.getText();
				int sep = rawText.indexOf(": ");
				String senderName = sep > 0 ? rawText.substring(0, sep) : ch.getName();
				String msgText = sep > 0 ? rawText.substring(sep + 2) : rawText;
				ChatMessage msg = new ChatMessage(0, m.getTimestamp(), msgText, false, senderName, true, null);
				setSignalInfo(msg, frame);
				Platform.runLater(() -> appendIncoming(key, msg));
			}
			default -> {
			}
			}
		};

		contactListener = new ContactListener() {
			@Override
			public void onContactAdded(Contact contact) {
				String hex = MeshcoreUtils.hex(contact.getPubkey());
				Platform.runLater(() -> {
					for (int i = 0; i < contacts.size(); i++) {
						if (MeshcoreUtils.hex(contacts.get(i).getPubkey()).equals(hex)) {
							contacts.set(i, contact);
							return;
						}
					}
					contacts.add(contact);
					discoveredContacts.removeIf(d -> MeshcoreUtils.hex(d.getPubkey()).equals(hex));
				});
			}

			@Override
			public void onContactUpdated(Contact contact) {
				String hex = MeshcoreUtils.hex(contact.getPubkey());
				Platform.runLater(() -> {
					for (int i = 0; i < contacts.size(); i++) {
						if (MeshcoreUtils.hex(contacts.get(i).getPubkey()).equals(hex)) {
							contacts.set(i, contact);
							return;
						}
					}
				});
			}

			@Override
			public void onContactRemoved(Contact contact) {
				String hex = MeshcoreUtils.hex(contact.getPubkey());
				Platform.runLater(() -> contacts.removeIf(c -> MeshcoreUtils.hex(c.getPubkey()).equals(hex)));
			}
		};

		sendConfirmedListener = push -> {
			String tagHex = MeshcoreUtils.hex(push.getTag());
			Platform.runLater(() -> {
				for (ObservableList<ChatMessage> msgs : loadedConversations.values()) {
					for (ChatMessage msg : msgs) {
						if (tagHex.equals(msg.getTag())) {
							msg.setConfirmed(true);
							int idx = msgs.indexOf(msg);
							if (idx >= 0)
								msgs.set(idx, msg);
							return;
						}
					}
				}
			});
		};

		newAdvertListener = push -> {
			String hex = MeshcoreUtils.hex(push.getPubkey());
			Platform.runLater(() -> {
				boolean alreadySaved = contacts.stream().anyMatch(c -> MeshcoreUtils.hex(c.getPubkey()).equals(hex));
				if (!alreadySaved) {
					boolean alreadyDiscovered = discoveredContacts.stream()
							.anyMatch(c -> MeshcoreUtils.hex(c.getPubkey()).equals(hex));
					if (!alreadyDiscovered) {
						discoveredContacts.add(push);
					}
				}
			});
		};

		companion.registerFrameListener(MessageFrameGroup.class, messageListener);
		companion.getConfig().addContactListener(contactListener);
		companion.registerFrameListener(SendConfirmedPush.class, sendConfirmedListener);
		companion.registerFrameListener(NewAdvertPush.class, newAdvertListener);

		// Wait for the library to complete its initial contacts sync (which may have
		// started before our listeners were registered), then load the authoritative
		// snapshot. awaitContactsSync returns only after the atomic commit of the
		// contacts map, so getSavedContacts() is always consistent here.
		new Thread(() -> {
			try {
				companion.getConfig().awaitContactsSync(30_000);
			} catch (Exception e) {
				log.warn("Contacts sync await failed on connect", e);
			}
			if (companion != currentCompanion)
				return;
			companion.installAutosyncMessages();
			List<Contact> savedContacts = companion.getConfig().getSavedContacts();
			List<ChannelInfo> chs = new ArrayList<>(companion.getConfig().getChannels());
			Platform.runLater(() -> {
				if (companion != currentCompanion)
					return;
				contacts.setAll(savedContacts);
				channels.setAll(chs);
				if (deviceHex == null && companion.getSelfInfo() != null) {
					deviceHex = MeshcoreUtils.hex(companion.getSelfInfo().getPubkey());
				}
			});
		}, "meshcore-initial-load").start();
	}

	private void onCompanionDisconnected(MeshcoreCompanion companion) {
		if (companion != this.currentCompanion)
			return;
		stopKeepAlive();
		if (messageListener != null)
			companion.removeFrameListener(MessageFrameGroup.class, messageListener);
		if (contactListener != null)
			companion.getConfig().removeContactListener(contactListener);
		if (sendConfirmedListener != null)
			companion.removeFrameListener(SendConfirmedPush.class, sendConfirmedListener);
		if (newAdvertListener != null)
			companion.removeFrameListener(NewAdvertPush.class, newAdvertListener);
		messageListener = null;
		contactListener = null;
		sendConfirmedListener = null;
		newAdvertListener = null;
		this.currentCompanion = null;
		authenticatedContacts.clear();
		Platform.runLater(() -> {
			contacts.clear();
			channels.clear();
			discoveredContacts.clear();
			if (onAuthChanged != null)
				onAuthChanged.run();
		});
	}

	// ── Incoming messages ────────────────────────────────────────────────────

	private static void setSignalInfo(ChatMessage msg, MessageFrameGroup frame) {
		var rxLog = frame.getPairedLogFrame();
		if (rxLog == null)
			return;
		msg.setRxLog(rxLog);
		String snrRssi = String.format("SNR %.1f dB  RSSI %d dBm", rxLog.getSnr4() / 4.0, rxLog.getRssi());
		OtaFrame ota = rxLog.getOtaFrame();
		String pathInfo = "";
		if (ota != null && ota.path != null && ota.path.length > 0) {
			int sz = Math.max(1, ota.hashSize);
			int hops = ota.path.length / sz;
			StringBuilder pathStr = new StringBuilder();
			for (int i = 0; i < ota.path.length; i += sz) {
				if (pathStr.length() > 0)
					pathStr.append(' ');
				pathStr.append(MeshcoreUtils.hex(Arrays.copyOfRange(ota.path, i, Math.min(i + sz, ota.path.length))));
			}
			pathInfo = String.format("  · %dB hash  %d hop  · %s", sz, hops, pathStr);
		}
		msg.setSignalInfo(snrRssi + pathInfo);
	}

	private void appendIncoming(String key, ChatMessage msg) {
		ObservableList<ChatMessage> msgs = getMessages(key);
		msg.setId(msgs.size());
		msgs.add(msg);
		if (deviceHex != null)
			ChatStore.appendMessage(deviceHex, key, msg);
		if (!(key.equals(activeConversationKey) && conversationAtBottom)) {
			unreadCountProperty(key).set(unreadCountProperty(key).get() + 1);
			if (isChannelKey(key))
				totalGroupUnread.set(totalGroupUnread.get() + 1);
			else
				totalContactUnread.set(totalContactUnread.get() + 1);
		}
	}

	// ── Accessors ────────────────────────────────────────────────────────────

	public ObservableList<Contact> getContacts() {
		return contacts;
	}

	public ObservableList<NewAdvertPush> getDiscoveredContacts() {
		return discoveredContacts;
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

	public ReadOnlyIntegerProperty totalContactUnreadProperty() {
		return totalContactUnread;
	}

	public ReadOnlyIntegerProperty totalGroupUnreadProperty() {
		return totalGroupUnread;
	}

	public void setActiveConversation(String key) {
		activeConversationKey = key;
		conversationAtBottom = false;
	}

	public void notifyAtBottom(String key) {
		if (key == null || !key.equals(activeConversationKey))
			return;
		conversationAtBottom = true;
		markRead(key);
	}

	public void markRead(String conversationKey) {
		int prev = unreadCountProperty(conversationKey).get();
		if (prev > 0) {
			unreadCountProperty(conversationKey).set(0);
			if (isChannelKey(conversationKey))
				totalGroupUnread.set(Math.max(0, totalGroupUnread.get() - prev));
			else
				totalContactUnread.set(Math.max(0, totalContactUnread.get() - prev));
		}
	}

	private boolean isChannelKey(String key) {
		return key.startsWith("ch_");
	}

	// ── Auth state ───────────────────────────────────────────────────────────

	public boolean isAuthenticated(Contact contact) {
		return authenticatedContacts.contains(contactKey(contact));
	}

	// ── History ─────────────────────────────────────────────────────────────

	public void clearHistory(String conversationKey) {
		ObservableList<ChatMessage> msgs = loadedConversations.get(conversationKey);
		if (msgs != null)
			msgs.clear();
		if (deviceHex != null)
			ChatStore.clear(deviceHex, conversationKey);
	}

	public void clearAllHistory() {
		for (ObservableList<ChatMessage> msgs : loadedConversations.values())
			msgs.clear();
		if (deviceHex != null)
			ChatStore.clearAll(deviceHex);
	}

	// ── Sending ──────────────────────────────────────────────────────────────

	public void sendToContact(Contact contact, String text, SendMode mode, MessageTextType txtType, Runnable onComplete,
			Consumer<String> onTextReturn) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		long now = Instant.now().getEpochSecond();
		String key = contactKey(contact);
		ObservableList<ChatMessage> msgs = getMessages(key);
		ChatMessage msg = new ChatMessage(msgs.size(), now, text, true, "Me", false, null);
		if (txtType == MessageTextType.TXT_TYPE_CLI_DATA)
			msg.setTxtType(txtType.name());
		msgs.add(msg);
		byte[] prefix6 = MeshcoreUtils.prefix6(contact.getPubkey());

		new Thread(() -> {
			try {
				c.awaitAvailable(30_000L);
			} catch (TimeoutException | InterruptedException e) {
				log.warn("Device not available for send to {}", contact.getName(), e);
				Platform.runLater(() -> {
					msgs.remove(msg);
					onTextReturn.accept(text);
					onComplete.run();
				});
				return;
			}
			// CLI sends get no SendConfirmedPush from the firmware; always use ASYNC path
			// and mark the message stored immediately without waiting for an ACK.
			if (txtType == MessageTextType.TXT_TYPE_CLI_DATA) {
				try {
					Platform.runLater(onComplete);
					if (deviceHex != null)
						ChatStore.appendMessage(deviceHex, key, msg);
					Sent sent = c.sendTxtMsgAsync(txtType, prefix6, 0, now, text);
					byte[] ackTag = sent.getAckIdOrTag();
					// Skip tag tracking when tag is all-zero (no ACK expected for CLI)
					if (ackTag != null && !Arrays.equals(ackTag, new byte[4])) {
						String tagHex = MeshcoreUtils.hex(ackTag);
						Platform.runLater(() -> updateTag(msgs, msg, tagHex, false, key));
					} else {
						Platform.runLater(() -> {
							msg.setConfirmed(true);
							int idx = msgs.indexOf(msg);
							if (idx >= 0)
								msgs.set(idx, msg);
						});
					}
				} catch (Exception e) {
					log.error("Failed to send CLI message to {}", contact.getName(), e);
				}
				return;
			}
			switch (mode) {
			case ASYNC -> {
				try {
					onComplete.run();
					if (deviceHex != null)
						ChatStore.appendMessage(deviceHex, key, msg);
					Sent sent = c.sendTxtMsgAsync(txtType, prefix6, 0, now, text);
					String tagHex = MeshcoreUtils.hex(sent.getAckIdOrTag());
					Platform.runLater(() -> updateTag(msgs, msg, tagHex, false, key));
				} catch (Exception e) {
					log.error("Failed to send message to {} (mode=ASYNC)", contact.getName(), e);
				}
			}
			case SYNC -> {
				try {
					var confirm = c.sendTxtMsg(txtType, prefix6, 0, now, text);
					String tagHex = MeshcoreUtils.hex(confirm.getTag());
					if (deviceHex != null)
						ChatStore.appendMessage(deviceHex, key, msg);
					Platform.runLater(() -> {
						updateTag(msgs, msg, tagHex, true, key);
						onComplete.run();
					});
				} catch (Exception e) {
					log.error("SYNC send failed to {}", contact.getName(), e);
					Platform.runLater(() -> {
						msgs.remove(msg);
						onTextReturn.accept(text);
						onComplete.run();
					});
				}
			}
			case RETRY -> {
				try {
					var confirm = c.sendTxtMsgWithRetry(txtType, prefix6, now, text);
					String tagHex = MeshcoreUtils.hex(confirm.getTag());
					if (deviceHex != null)
						ChatStore.appendMessage(deviceHex, key, msg);
					Platform.runLater(() -> {
						updateTag(msgs, msg, tagHex, true, key);
						onComplete.run();
					});
				} catch (Exception e) {
					log.error("RETRY send failed to {}", contact.getName(), e);
					Platform.runLater(() -> {
						msgs.remove(msg);
						onTextReturn.accept(text);
						onComplete.run();
					});
				}
			}
			}
		}, "meshcore-send").start();
	}

	private void updateTag(ObservableList<ChatMessage> msgs, ChatMessage msg, String tagHex, boolean confirmed,
			String key) {
		msg.setTag(tagHex);
		msg.setConfirmed(confirmed);
		int idx = msgs.indexOf(msg);
		if (idx >= 0)
			msgs.set(idx, msg);
		if (deviceHex != null)
			ChatStore.save(deviceHex, key, new ArrayList<>(msgs));
	}

	public void sendToChannel(ChannelInfo channel, String text) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		long now = Instant.now().getEpochSecond();
		String key = channelKey(channel);
		ObservableList<ChatMessage> msgs = getMessages(key);
		ChatMessage msg = new ChatMessage(msgs.size(), now, text, true, "Me", false, null);
		msgs.add(msg);
		if (deviceHex != null)
			ChatStore.appendMessage(deviceHex, key, msg);

		new Thread(() -> {
			try {
				var resp = c.sendChannelTxtMessage(MessageTextType.TXT_TYPE_PLAIN, channel.getId(), now, text);
				String tagHex = resp instanceof Sent sent ? MeshcoreUtils.hex(sent.getAckIdOrTag()) : "ch_" + now;
				Platform.runLater(() -> updateTag(msgs, msg, tagHex, true, key));
				startRepeatMonitor(c, msgs, msg, text);
			} catch (Exception e) {
				log.error("Failed to send message to channel {}", channel.getName(), e);
			}
		}, "meshcore-send").start();
	}

	private void startRepeatMonitor(MeshcoreCompanion c, ObservableList<ChatMessage> msgs, ChatMessage msg,
			String sentText) {
		long deadline = System.currentTimeMillis() + 30_000;
		FrameListener<LogRXDataPush> repeatListener = push -> {
			if (System.currentTimeMillis() > deadline)
				return;
			if (push.tryDecryptGrpTxt()) {
				OtaFrame ota = push.getOtaFrame();
				if (ota instanceof OtaGroupFrame grp && msg.getTimestamp() == grp.decryptedTimestamp
						&& sentText.equals(grp.decryptedText)) {
					Platform.runLater(() -> {
						msg.setRepeatCount(msg.getRepeatCount() + 1);
						int idx = msgs.indexOf(msg);
						if (idx >= 0)
							msgs.set(idx, msg);
					});
				}
			}
		};
		c.registerFrameListener(LogRXDataPush.class, repeatListener);
		new Thread(() -> {
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException ignored) {
			}
			c.removeFrameListener(LogRXDataPush.class, repeatListener);
		}, "meshcore-repeat-cleanup").start();
	}

	// ── Contact management ───────────────────────────────────────────────────

	public void addContactByPubkey(String pubkeyHex, String displayName) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		byte[] pubkey = MeshcoreUtils.fromHex(pubkeyHex);
		long now = Instant.now().getEpochSecond();
		CmdAddUpdateContact cmd = new CmdAddUpdateContact(pubkey, AdvertType.ADV_TYPE_CHAT, 0,
				new byte[Settings.MAX_PATH_SIZE], displayName, now, null, null, null);
		new Thread(() -> {
			try {
				var resp = c.sendFrameWithResult(cmd, 5000);
				if (resp instanceof Error err) {
					log.warn("Add contact {} failed: {}", displayName, err.getCode());
					return;
				}
				log.info("Added contact {}", displayName);
				var fetched = c.sendFrameWithResult(new CmdGetContactByKey(pubkey), 2000);
				if (fetched instanceof Contact contact) {
					updateContactInList(contact);
				}
			} catch (Exception e) {
				log.error("Failed to add contact {}", displayName, e);
			}
		}, "meshcore-add-contact").start();
	}

	public void importDiscovered(NewAdvertPush advert, Consumer<String> onError, Runnable onSuccess) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				var resp = c.sendFrameWithResult(advert.getCmdAddUpdateContact(), 5000);
				if (resp instanceof Error error) {
					String msg = switch (error.getCode()) {
					case TABLE_FULL ->
						"Contact table is full. Remove a contact or enable 'Overwrite oldest' in auto-add settings.";
					default -> "Device returned error: " + error.getCode();
					};
					log.warn("Import contact {} failed: {}", advert.getName(), error.getCode());
					Platform.runLater(() -> onError.accept(msg));
					return;
				}
				log.info("Imported discovered contact {}", advert.getName());
				var fetched = c.sendFrameWithResult(new CmdGetContactByKey(advert.getPubkey()), 2000);
				if (fetched instanceof Contact contact) {
					updateContactInList(contact);
					if (onSuccess != null)
						Platform.runLater(onSuccess);
				}
			} catch (Exception e) {
				log.error("Failed to import contact {}", advert.getName(), e);
				Platform.runLater(() -> onError.accept(e.getMessage()));
			}
		}, "meshcore-import-contact").start();
	}

	public void removeContact(Contact contact) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().removeContact(contact.getPubkey());
				log.info("Removed contact {}", contact.getName());
				String hex = MeshcoreUtils.hex(contact.getPubkey());
				Platform.runLater(() -> contacts.removeIf(ct -> MeshcoreUtils.hex(ct.getPubkey()).equals(hex)));
			} catch (Exception e) {
				log.error("Failed to remove contact {}", contact.getName(), e);
			}
		}, "meshcore-remove-contact").start();
	}

	public void toggleFavourite(Contact contact) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		// Patch flags byte in a clone of the contact's raw bytes to preserve
		// path/advertTS
		byte[] cmdBytes = contact.getBytes().clone();
		final int FLAGS_OFFSET = 34; // 1 (type) + 32 (pubkey) + 1 (advert type)
		if (contact.hasFlag(ContactFlags.FAVOURITE))
			cmdBytes[FLAGS_OFFSET] &= ~ContactFlags.FAVOURITE.mask();
		else
			cmdBytes[FLAGS_OFFSET] |= ContactFlags.FAVOURITE.mask();
		CmdAddUpdateContact cmd = new CmdAddUpdateContact(cmdBytes);
		new Thread(() -> {
			try {
				var resp = c.sendFrameWithResult(cmd, 5000);
				if (resp instanceof Error err) {
					log.warn("Toggle favourite for {} failed: {}", contact.getName(), err.getCode());
					return;
				}
				log.info("Toggled favourite for {}", contact.getName());
				var fetched = c.sendFrameWithResult(new CmdGetContactByKey(contact.getPubkey()), 2000);
				if (fetched instanceof Contact updated) {
					updateContactInList(updated);
				}
			} catch (Exception e) {
				log.error("Failed to toggle favourite for {}", contact.getName(), e);
			}
		}, "meshcore-favourite").start();
	}

	private void updateContactInList(Contact contact) {
		String hex = MeshcoreUtils.hex(contact.getPubkey());
		Platform.runLater(() -> {
			for (int i = 0; i < contacts.size(); i++) {
				if (MeshcoreUtils.hex(contacts.get(i).getPubkey()).equals(hex)) {
					contacts.set(i, contact);
					return;
				}
			}
			contacts.add(contact);
			discoveredContacts.removeIf(d -> MeshcoreUtils.hex(d.getPubkey()).equals(hex));
		});
	}

	// ── Channel management ───────────────────────────────────────────────────

	public void addPublicChannel(String name) {
		doAddChannel(name, Settings.PUBLIC_GROUP_PSK.clone());
	}

	public void addHashChannel(String name) {
		String hashName = name.startsWith("#") ? name : "#" + name;
		doAddChannel(hashName, null);
	}

	public void addPrivateChannel(String name, byte[] key) {
		doAddChannel(name, key);
	}

	private void doAddChannel(String name, byte[] key) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setChannel(name, key);
				List<ChannelInfo> chs = new ArrayList<>(c.getConfig().getChannels());
				Platform.runLater(() -> channels.setAll(chs));
				log.info("Added channel {}", name);
			} catch (Exception e) {
				log.error("Failed to add channel {}", name, e);
			}
		}, "meshcore-add-channel").start();
	}

	public void removeChannel(ChannelInfo channel) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().setChannel(channel.getId(), null, new byte[16]);
				List<ChannelInfo> chs = new ArrayList<>(c.getConfig().getChannels());
				Platform.runLater(() -> channels.setAll(chs));
				log.info("Removed channel {}", channel.getName());
			} catch (Exception e) {
				log.error("Failed to remove channel {}", channel.getName(), e);
			}
		}, "meshcore-remove-channel").start();
	}

	// ── Login / logout ───────────────────────────────────────────────────────

	public void loginToRoom(Contact contact, String password) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.login(contact.getPubkey(), password);
				authenticatedContacts.add(contactKey(contact));
				log.info("Logged in to room {}", contact.getName());
				if (onAuthChanged != null)
					Platform.runLater(onAuthChanged);
			} catch (Exception e) {
				log.error("Login to room {} failed", contact.getName(), e);
			}
		}, "meshcore-login").start();
	}

	public void logoutFromRoom(Contact contact) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		stopKeepAlive();
		new Thread(() -> {
			try {
				c.logout(contact.getPubkey());
				authenticatedContacts.remove(contactKey(contact));
				log.info("Logged out from room {}", contact.getName());
				if (onAuthChanged != null)
					Platform.runLater(onAuthChanged);
			} catch (Exception e) {
				log.error("Logout from room {} failed", contact.getName(), e);
			}
		}, "meshcore-logout").start();
	}

	// ── Keep-alive (room server) ─────────────────────────────────────────────

	public void startKeepAlive(Contact contact) {
		stopKeepAlive();
		keepAliveContact = contact;
		String key = contactKey(contact);
		Thread t = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(30_000);
				} catch (InterruptedException e) {
					break;
				}
				MeshcoreCompanion c = currentCompanion;
				if (c == null || contact != keepAliveContact)
					break;
				try {
					long syncSince = lastRoomTimestamp.getOrDefault(key, 0L);
					c.sendKeepAlive(contact.getPubkey(), syncSince);
					log.debug("Keep-alive sent to room {}", contact.getName());
				} catch (Exception e) {
					log.warn("Keep-alive failed for {}", contact.getName(), e);
				}
			}
		}, "meshcore-keepalive");
		t.setDaemon(true);
		keepAliveThread = t;
		t.start();
	}

	public void stopKeepAlive() {
		keepAliveContact = null;
		Thread t = keepAliveThread;
		keepAliveThread = null;
		if (t != null)
			t.interrupt();
	}

	// ── Advert ───────────────────────────────────────────────────────────────

	public void sendSingleAdvert() {
		sendAdvert(false);
	}

	public void sendFloodAdvert() {
		sendAdvert(true);
	}

	private void sendAdvert(boolean flood) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.sendFrame(new cz.bliksoft.meshcore.frames.cmd.CmdSendSelfAdvert(
						flood ? cz.bliksoft.meshcore.frames.cmd.CmdSendSelfAdvert.AdvertMethod.FLOOD
								: cz.bliksoft.meshcore.frames.cmd.CmdSendSelfAdvert.AdvertMethod.SINGLE));
				log.info("Sent {} advert", flood ? "flood" : "single-hop");
			} catch (Exception e) {
				log.error("Failed to send advert", e);
			}
		}, "meshcore-advert").start();
	}

	// ── Resync ───────────────────────────────────────────────────────────────

	public void resyncContact(Contact contact) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				var fetched = c.sendFrameWithResult(new CmdGetContactByKey(contact.getPubkey()), 2000);
				if (fetched instanceof Contact updated) {
					updateContactInList(updated);
					log.info("Resynced contact {}", contact.getName());
				} else {
					log.warn("Resync contact {} returned unexpected frame: {}", contact.getName(), fetched);
				}
			} catch (Exception e) {
				log.error("Failed to resync contact {}", contact.getName(), e);
			}
		}, "meshcore-resync").start();
	}

	// ── Path reset ───────────────────────────────────────────────────────────

	public void resetPath(Contact contact) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return;
		new Thread(() -> {
			try {
				c.getConfig().resetPath(contact.getPubkey());
				log.info("Reset path for {}", contact.getName());
			} catch (Exception e) {
				log.error("Failed to reset path for {}", contact.getName(), e);
			}
		}, "meshcore-reset-path").start();
	}

	// ── Keys ─────────────────────────────────────────────────────────────────

	public boolean isConnected() {
		return currentCompanion != null;
	}

	public String contactKey(Contact c) {
		return MeshcoreUtils.hex(c.getPubkey());
	}

	public String channelKey(ChannelInfo ch) {
		return "ch_" + ch.getName();
	}

	public String resolvePathPrefix(String hex) {
		MeshcoreCompanion c = currentCompanion;
		if (c == null)
			return null;
		Contact contact = c.getConfig().getContact(MeshcoreUtils.fromHex(hex));
		return contact != null ? contact.getName() : null;
	}
}
