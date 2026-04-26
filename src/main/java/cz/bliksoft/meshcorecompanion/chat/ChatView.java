package cz.bliksoft.meshcorecompanion.chat;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.bliksoft.javautils.app.BSApp;
import cz.bliksoft.meshcore.frames.FrameConstants.MessageTextType;
import cz.bliksoft.meshcore.frames.cmd.CmdSendTxtMsg;
import cz.bliksoft.meshcore.otaframe.OtaFrame;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import cz.bliksoft.meshcorecompanion.model.ChatMessage;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import static cz.bliksoft.meshcore.frames.FrameConstants.MessageTextType.*;

/**
 * Reusable right-hand chat panel: message list + compose bar with send-mode
 * selector.
 *
 * <p>
 * The send callback receives (conversationKey, text, sendMode).
 */
class ChatView extends VBox {

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

	@FunctionalInterface
	interface SendCallback {
		void send(String key, String text, SendMode mode, MessageTextType txtType, Runnable onComplete,
				Consumer<String> onTextReturn);
	}

	private final ListView<ChatMessage> listView = new ListView<>();
	private final TextArea inputField = new TextArea();
	private final ComboBox<SendMode> modeBox = new ComboBox<>();
	private final ToggleButton cliToggle = new ToggleButton("CLI");
	private final Label byteCountLabel = new Label();
	private final HBox sendBar;

	private int maxMsgBytes = CmdSendTxtMsg.MAX_TEXT_BYTES;
	private String conversationKey;
	private SendCallback sendCallback;
	private ListChangeListener<ChatMessage> messageChangeListener;
	private boolean atBottom = true;
	private MessageTextType currentTxtType = TXT_TYPE_PLAIN;

	ChatView() {
		getStyleClass().add("chat-view");

		listView.setCellFactory(lv -> new MessageCell());
		listView.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				ChatMessage sel = listView.getSelectionModel().getSelectedItem();
				if (sel != null && !sel.isOutgoing())
					reply(sel);
			}
		});
		VBox.setVgrow(listView, Priority.ALWAYS);

		// Track whether the user is scrolled to the bottom; also suppress the
		// horizontal scrollbar — chat bubbles size to content, so the ListView's
		// natural prefWidth can exceed the viewport for long messages, but we never
		// want horizontal scrolling in a chat view.
		listView.skinProperty().addListener((obs, o, skin) -> {
			if (skin == null)
				return;
			ScrollBar vbar = (ScrollBar) listView.lookup(".scroll-bar:vertical");
			if (vbar != null) {
				vbar.valueProperty().addListener((sObs, sO, sN) -> {
					double max = vbar.getMax();
					boolean was = atBottom;
					atBottom = max <= 0 || sN.doubleValue() >= max - vbar.getVisibleAmount() / 2;
					if (atBottom && !was && conversationKey != null)
						ChatManager.getInstance().notifyAtBottom(conversationKey);
				});
			}
			ScrollBar hbar = (ScrollBar) listView.lookup(".scroll-bar:horizontal");
			if (hbar != null) {
				hbar.setVisible(false);
				hbar.setManaged(false);
			}
		});

		inputField.setPromptText("Type a message…");
		inputField.setWrapText(true);
		inputField.setPrefRowCount(3);

		byteCountLabel.getStyleClass().add("byte-count");
		byteCountLabel.setMaxWidth(Double.MAX_VALUE);
		byteCountLabel.setAlignment(Pos.CENTER_RIGHT);
		inputField.textProperty().addListener((obs, old, text) -> updateByteCount(text));
		updateByteCount("");

		modeBox.getItems().addAll(SendMode.values());
		modeBox.setValue(SendMode.SYNC);
		modeBox.setTooltip(new javafx.scene.control.Tooltip(
				"Async – fire and forget\nSync – wait for ACK\nRetry – up to 3 attempts with flood fallback"));

		cliToggle.setVisible(false);
		cliToggle.setManaged(false);
		cliToggle.selectedProperty().addListener((obs, o, selected) -> {
			currentTxtType = selected ? TXT_TYPE_CLI_DATA : TXT_TYPE_PLAIN;
			modeBox.setDisable(selected);
		});

		VBox inputContainer = new VBox(2, inputField, byteCountLabel);
		VBox.setVgrow(inputField, Priority.ALWAYS);
		HBox.setHgrow(inputContainer, Priority.ALWAYS);

		Button sendBtn = new Button("Send");
		sendBtn.setOnAction(e -> doSend());
		inputField.setOnKeyPressed(e -> {
			if (e.getCode() != KeyCode.ENTER)
				return;
			boolean enterSends = "true".equals(BSApp.getProperty("chat.enterSends"));
			if (enterSends) {
				if (!e.isShiftDown() && !e.isControlDown()) {
					e.consume();
					doSend();
				}
			} else {
				if (e.isControlDown()) {
					e.consume();
					doSend();
				}
			}
		});

		sendBar = new HBox(4, inputContainer, cliToggle, modeBox, sendBtn);
		sendBar.setPadding(new Insets(4));
		sendBar.setAlignment(Pos.BOTTOM_LEFT);

		getChildren().addAll(listView, sendBar);
		setDisable(true);
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"{}|\\\\^`\\[\\]]+",
			Pattern.CASE_INSENSITIVE);

	private static String findUrl(String text) {
		Matcher m = URL_PATTERN.matcher(text);
		return m.find() ? m.group() : null;
	}

	private static List<String> pathHexes(OtaFrame ota) {
		if (ota == null || ota.path == null || ota.path.length == 0)
			return List.of();
		int sz = Math.max(1, ota.hashSize);
		List<String> list = new ArrayList<>();
		for (int i = 0; i + sz <= ota.path.length; i += sz)
			list.add(MeshcoreUtils.hex(Arrays.copyOfRange(ota.path, i, i + sz)));
		return list;
	}

	private static void copyToClipboard(String text) {
		ClipboardContent cc = new ClipboardContent();
		cc.putString(text);
		Clipboard.getSystemClipboard().setContent(cc);
	}

	void reply(ChatMessage msg) {
		if (sendBar.isDisabled())
			return;
		String mention = "@[" + msg.getSenderName() + "] ";
		String current = inputField.getText();
		inputField.setText(current.startsWith(mention) ? current : mention + current);
		inputField.end();
		inputField.requestFocus();
	}

	// ── Conversation ─────────────────────────────────────────────────────────

	void setConversation(String key, SendCallback sendCallback) {
		if (messageChangeListener != null && conversationKey != null) {
			ObservableList<ChatMessage> old = ChatManager.getInstance().getMessages(conversationKey);
			old.removeListener(messageChangeListener);
		}
		this.conversationKey = key;
		this.sendCallback = sendCallback;

		if (key == null) {
			ChatManager.getInstance().setActiveConversation(null);
			listView.setItems(null);
			setDisable(true);
			return;
		}

		ObservableList<ChatMessage> msgs = ChatManager.getInstance().getMessages(key);
		listView.setItems(msgs);
		setDisable(false);
		sendBar.setDisable(sendCallback == null);
		atBottom = true;
		ChatManager.getInstance().setActiveConversation(key);
		if (!msgs.isEmpty())
			listView.scrollTo(msgs.size() - 1);
		ChatManager.getInstance().notifyAtBottom(key);

		messageChangeListener = change -> {
			if (atBottom && !msgs.isEmpty())
				listView.scrollTo(msgs.size() - 1);
		};
		msgs.addListener(messageChangeListener);
		inputField.requestFocus();
	}

	void setSendEnabled(boolean enabled) {
		sendBar.setDisable(!enabled);
	}

	void setModeVisible(boolean visible) {
		modeBox.setVisible(visible);
		modeBox.setManaged(visible);
	}

	void setCliToggleVisible(boolean visible) {
		cliToggle.setVisible(visible);
		cliToggle.setManaged(visible);
	}

	void setTxtType(MessageTextType type) {
		currentTxtType = type;
		cliToggle.setSelected(type == TXT_TYPE_CLI_DATA);
	}

	void setMaxMessageBytes(int max) {
		maxMsgBytes = max;
		updateByteCount(inputField.getText());
	}

	private void updateByteCount(String text) {
		int bytes = text.getBytes(StandardCharsets.UTF_8).length;
		byteCountLabel.setText(bytes + " / " + maxMsgBytes);
		byteCountLabel.getStyleClass().removeAll("byte-count-warn", "byte-count-over");
		if (bytes > maxMsgBytes)
			byteCountLabel.getStyleClass().add("byte-count-over");
		else if (bytes > maxMsgBytes * 0.85)
			byteCountLabel.getStyleClass().add("byte-count-warn");
	}

	private void doSend() {
		String text = inputField.getText().trim();
		if (text.isEmpty() || conversationKey == null || sendCallback == null)
			return;
		inputField.clear();
		SendMode mode = modeBox.getValue();
		boolean blocking = mode == SendMode.SYNC || mode == SendMode.RETRY;
		if (blocking)
			sendBar.setDisable(true);
		String sentKey = conversationKey;
		Runnable onComplete = blocking ? () -> {
			if (sentKey.equals(conversationKey)) {
				sendBar.setDisable(false);
				inputField.requestFocus();
			}
		} : () -> {
		};
		sendCallback.send(conversationKey, text, mode, currentTxtType, onComplete, t -> {
			if (sentKey.equals(conversationKey)) {
				inputField.setText(t);
				inputField.end();
			}
		});
	}

	// ── Message cell ─────────────────────────────────────────────────────────

	private class MessageCell extends ListCell<ChatMessage> {

		private final Label textLabel = new Label();
		private final Label metaLabel = new Label();
		private final VBox content = new VBox(2, textLabel, metaLabel);
		private final HBox wrapper = new HBox(content);

		MessageCell() {
			textLabel.setWrapText(true);
			metaLabel.setWrapText(true);
			metaLabel.getStyleClass().add("chat-meta");
			content.setPadding(new Insets(4, 8, 4, 8));
			HBox.setHgrow(content, Priority.NEVER);
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}

		// Give the virtual flow an accurate height estimate so it doesn't
		// underallocate the cell pool and log "index exceeds maxCellCount".
		@Override
		protected double computePrefHeight(double width) {
			if (isEmpty() || getItem() == null) {
				return super.computePrefHeight(width);
			}
			double insetH = snappedTopInset() + snappedBottomInset();
			double insetW = snappedLeftInset() + snappedRightInset();
			double availW = (width > 0 ? width : getWidth()) - insetW;
			// Also cap by the bubble's CSS max-width: without this, height is computed
			// assuming the full cell width, but the bubble renders narrower, needs more
			// lines, and the ListView under-allocates height causing ellipsis truncation.
			double maxW = content.getMaxWidth();
			if (maxW > 0)
				availW = Math.min(availW, maxW);
			// Bubble is at most as wide as the cell; text wraps if content is wider.
			double bubbleW = availW > 0 ? Math.min(content.prefWidth(-1), availW) : content.prefWidth(-1);
			return content.prefHeight(bubbleW) + insetH;
		}

		@Override
		protected void updateItem(ChatMessage msg, boolean empty) {
			super.updateItem(msg, empty);
			if (empty || msg == null) {
				setGraphic(null);
				setContextMenu(null);
				return;
			}

			textLabel.setText(msg.getText());

			String time = LocalDateTime.ofInstant(Instant.ofEpochSecond(msg.getTimestamp()), ZoneId.systemDefault())
					.format(TIME_FMT);

			content.getStyleClass().removeAll("chat-bubble-in", "chat-bubble-out", "chat-bubble-cli");
			if (msg.isCli())
				content.getStyleClass().add("chat-bubble-cli");
			if (msg.isOutgoing()) {
				String status = msg.isConfirmed() ? " ✓" : (msg.getTag() == null ? " ⏳" : " …");
				String repeats = msg.getRepeatCount() > 0 ? "  🔁 " + msg.getRepeatCount() : "";
				metaLabel.setText(time + status + repeats);
				content.getStyleClass().add("chat-bubble-out");
				wrapper.setAlignment(Pos.CENTER_RIGHT);
			} else {
				String signal = msg.getSignalInfo() != null ? "  · " + msg.getSignalInfo() : "";
				metaLabel.setText(msg.getSenderName() + "  " + time + signal);
				content.getStyleClass().add("chat-bubble-in");
				wrapper.setAlignment(Pos.CENTER_LEFT);
			}

			setGraphic(wrapper);
			setContextMenu(buildContextMenu(msg));
		}

		private ContextMenu buildContextMenu(ChatMessage msg) {
			ContextMenu menu = new ContextMenu();

			MenuItem copyText = new MenuItem("Copy text");
			copyText.setOnAction(e -> copyToClipboard(msg.getText()));
			menu.getItems().add(copyText);

			String url = findUrl(msg.getText());
			if (url != null) {
				MenuItem copyLink = new MenuItem("Copy link");
				copyLink.setOnAction(e -> copyToClipboard(url));
				menu.getItems().add(copyLink);

				MenuItem openLink = new MenuItem("Open link");
				openLink.setOnAction(e -> {
					try {
						if (Desktop.isDesktopSupported())
							Desktop.getDesktop().browse(URI.create(url));
					} catch (Exception ex) {
						// unsupported or malformed URI — ignore
					}
				});
				menu.getItems().add(openLink);
			}

			OtaFrame ota = msg.getRxLog() != null ? msg.getRxLog().getOtaFrame() : null;
			List<String> hexes = pathHexes(ota);
			if (!hexes.isEmpty()) {
				menu.getItems().add(new SeparatorMenuItem());

				MenuItem copyPath = new MenuItem("Copy path");
				copyPath.setOnAction(e -> copyToClipboard(String.join(" ", hexes)));
				menu.getItems().add(copyPath);

				MenuItem copyDetailedPath = new MenuItem("Copy detailed path");
				copyDetailedPath.setOnAction(e -> {
					StringBuilder sb = new StringBuilder();
					for (String h : hexes) {
						String name = ChatManager.getInstance().resolvePathPrefix(h);
						sb.append(h);
						if (name != null)
							sb.append(" ").append(name);
						sb.append('\n');
					}
					copyToClipboard(sb.toString().stripTrailing());
				});
				menu.getItems().add(copyDetailedPath);
			}

			if (!msg.isOutgoing() && msg.getSenderHex() != null) {
				if (menu.getItems().stream().noneMatch(i -> i instanceof SeparatorMenuItem))
					menu.getItems().add(new SeparatorMenuItem());
				MenuItem copySender = new MenuItem("Copy sender prefix");
				copySender.setOnAction(e -> copyToClipboard(msg.getSenderHex()));
				menu.getItems().add(copySender);
			}

			if (!msg.isOutgoing()) {
				menu.getItems().add(new SeparatorMenuItem());
				MenuItem replyItem = new MenuItem("Reply");
				replyItem.setOnAction(e -> reply(msg));
				menu.getItems().add(replyItem);
			}

			return menu;
		}
	}
}
