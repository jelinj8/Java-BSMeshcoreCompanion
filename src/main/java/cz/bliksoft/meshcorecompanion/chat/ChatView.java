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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Reusable right-hand chat panel: message list + compose bar with send-mode selector.
 *
 * <p>The send callback receives (conversationKey, text, sendMode).
 */
class ChatView extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FunctionalInterface
    interface SendCallback {
        void send(String key, String text, SendMode mode);
    }

    private final ListView<ChatMessage> listView = new ListView<>();
    private final TextField inputField = new TextField();
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

        // Track whether the user is scrolled to the bottom
        listView.skinProperty().addListener((obs, o, skin) -> {
            if (skin == null) return;
            ScrollBar sb = (ScrollBar) listView.lookup(".scroll-bar:vertical");
            if (sb != null) {
                sb.valueProperty().addListener((sObs, sO, sN) -> {
                    double max = sb.getMax();
                    atBottom = max <= 0 || sN.doubleValue() >= max - sb.getVisibleAmount() / 2;
                });
            }
        });

        inputField.setPromptText("Type a message…");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        modeBox.getItems().addAll(SendMode.values());
        modeBox.setValue(SendMode.ASYNC);
        modeBox.setTooltip(new javafx.scene.control.Tooltip(
                "Async – fire and forget\nSync – wait for ACK\nRetry – up to 3 attempts with flood fallback"));

        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> doSend());
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) doSend();
        });

        sendBar = new HBox(4, inputField, modeBox, sendBtn);
        sendBar.setPadding(new Insets(4));
        sendBar.setAlignment(Pos.CENTER_LEFT);

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
        if (!msgs.isEmpty()) listView.scrollTo(msgs.size() - 1);

        messageChangeListener = change -> {
            if (atBottom && !msgs.isEmpty()) listView.scrollTo(msgs.size() - 1);
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
        if (text.isEmpty() || conversationKey == null || sendCallback == null) return;
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
            textLabel.setMaxWidth(380);
            metaLabel.getStyleClass().add("chat-meta");
            content.setPadding(new Insets(4, 8, 4, 8));
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) {
                setGraphic(null);
                return;
            }

            textLabel.setText(msg.getText());

            String time = LocalDateTime
                    .ofInstant(Instant.ofEpochSecond(msg.getTimestamp()), ZoneId.systemDefault())
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

            HBox.setHgrow(content, Priority.NEVER);
            wrapper.setPrefWidth(USE_COMPUTED_SIZE);
            setGraphic(wrapper);
        }
    }
}
