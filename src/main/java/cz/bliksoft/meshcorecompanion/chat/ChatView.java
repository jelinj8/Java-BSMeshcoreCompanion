package cz.bliksoft.meshcorecompanion.chat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

import cz.bliksoft.meshcorecompanion.model.ChatMessage;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

class ChatView extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ListView<ChatMessage> listView = new ListView<>();
    private final TextField inputField = new TextField();

    private String conversationKey;
    private BiConsumer<String, String> sendCallback;
    private ListChangeListener<ChatMessage> messageChangeListener;

    ChatView() {
        getStyleClass().add("chat-view");

        listView.setCellFactory(lv -> new MessageCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        inputField.setPromptText("Type a message…");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> doSend());
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) doSend();
        });

        HBox sendBar = new HBox(4, inputField, sendBtn);
        sendBar.setPadding(new Insets(4));
        sendBar.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(listView, sendBar);
        setDisable(true);
    }

    void setConversation(String key, BiConsumer<String, String> sendCallback) {
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
        ChatManager.getInstance().markRead(key);
        if (!msgs.isEmpty()) listView.scrollTo(msgs.size() - 1);

        messageChangeListener = change -> {
            if (!msgs.isEmpty()) listView.scrollTo(msgs.size() - 1);
        };
        msgs.addListener(messageChangeListener);
        inputField.requestFocus();
    }

    private void doSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || conversationKey == null || sendCallback == null) return;
        inputField.clear();
        sendCallback.accept(conversationKey, text);
    }

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
            setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
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

            if (msg.isOutgoing()) {
                String meta = time + (msg.isConfirmed() ? " ✓" : " …");
                metaLabel.setText(meta);
                content.getStyleClass().removeAll("chat-bubble-in", "chat-bubble-out");
                content.getStyleClass().add("chat-bubble-out");
                wrapper.setAlignment(Pos.CENTER_RIGHT);
            } else {
                metaLabel.setText(msg.getSenderName() + "  " + time);
                content.getStyleClass().removeAll("chat-bubble-in", "chat-bubble-out");
                content.getStyleClass().add("chat-bubble-in");
                wrapper.setAlignment(Pos.CENTER_LEFT);
            }

            HBox.setHgrow(content, Priority.NEVER);
            wrapper.setPrefWidth(USE_COMPUTED_SIZE);
            setGraphic(wrapper);
        }
    }
}
