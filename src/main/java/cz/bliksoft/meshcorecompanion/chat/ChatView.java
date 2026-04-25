package cz.bliksoft.meshcorecompanion.chat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import cz.bliksoft.meshcorecompanion.model.ChatMessage;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import cz.bliksoft.javautils.app.BSApp;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
		void send(String key, String text, SendMode mode);
	}

	private final ListView<ChatMessage> listView = new ListView<>();
	private final TextArea inputField = new TextArea();
	private final ComboBox<SendMode> modeBox = new ComboBox<>();
	private final HBox sendBar;

	private String conversationKey;
	private SendCallback sendCallback;
	private ListChangeListener<ChatMessage> messageChangeListener;
	private boolean atBottom = true;

	ChatView() {
		getStyleClass().add("chat-view");

		listView.setCellFactory(lv -> new MessageCell());
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
					atBottom = max <= 0 || sN.doubleValue() >= max - vbar.getVisibleAmount() / 2;
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
		HBox.setHgrow(inputField, Priority.ALWAYS);

		modeBox.getItems().addAll(SendMode.values());
		modeBox.setValue(SendMode.ASYNC);
		modeBox.setTooltip(new javafx.scene.control.Tooltip(
				"Async – fire and forget\nSync – wait for ACK\nRetry – up to 3 attempts with flood fallback"));

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

		sendBar = new HBox(4, inputField, modeBox, sendBtn);
		sendBar.setPadding(new Insets(4));
		sendBar.setAlignment(Pos.BOTTOM_LEFT);

		getChildren().addAll(listView, sendBar);
		setDisable(true);
	}

	void setConversation(String key, SendCallback sendCallback) {
		if (messageChangeListener != null && conversationKey != null) {
			ObservableList<ChatMessage> old = ChatManager.getInstance().getMessages(conversationKey);
			old.removeListener(messageChangeListener);
		}
		this.conversationKey = key;
		this.sendCallback = sendCallback;

		if (key == null) {
			listView.setItems(null);
			setDisable(true);
			return;
		}

		ObservableList<ChatMessage> msgs = ChatManager.getInstance().getMessages(key);
		listView.setItems(msgs);
		setDisable(false);
		sendBar.setDisable(sendCallback == null);
		atBottom = true;
		ChatManager.getInstance().markRead(key);
		if (!msgs.isEmpty())
			listView.scrollTo(msgs.size() - 1);

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

	private void doSend() {
		String text = inputField.getText().trim();
		if (text.isEmpty() || conversationKey == null || sendCallback == null)
			return;
		inputField.clear();
		sendCallback.send(conversationKey, text, modeBox.getValue());
	}

	// ── Message cell ─────────────────────────────────────────────────────────

	private static class MessageCell extends ListCell<ChatMessage> {

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
			// Bubble is at most as wide as the cell; text wraps if content is wider.
			double bubbleW = availW > 0 ? Math.min(content.prefWidth(-1), availW) : content.prefWidth(-1);
			return content.prefHeight(bubbleW) + insetH;
		}

		@Override
		protected void updateItem(ChatMessage msg, boolean empty) {
			super.updateItem(msg, empty);
			if (empty || msg == null) {
				setGraphic(null);
				return;
			}

			textLabel.setText(msg.getText());

			String time = LocalDateTime.ofInstant(Instant.ofEpochSecond(msg.getTimestamp()), ZoneId.systemDefault())
					.format(TIME_FMT);

			content.getStyleClass().removeAll("chat-bubble-in", "chat-bubble-out");
			if (msg.isOutgoing()) {
				String status = msg.getTag() == null ? " ⏳" : (msg.isConfirmed() ? " ✓" : " …");
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
		}
	}
}
