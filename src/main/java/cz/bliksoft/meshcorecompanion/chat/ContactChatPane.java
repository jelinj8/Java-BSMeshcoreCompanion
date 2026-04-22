package cz.bliksoft.meshcorecompanion.chat;

import cz.bliksoft.javautils.fx.controls.renderers.IconTextListCell;
import cz.bliksoft.meshcore.frames.resp.Contact;
import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ContactChatPane extends VBox {

    private final ChatManager chatManager = ChatManager.getInstance();

    public ContactChatPane() {
        TextField filter = new TextField();
        filter.setPromptText("Filter contacts…");

        ListView<Contact> contactList = new ListView<>(chatManager.getContacts());
        contactList.setCellFactory(lv -> new IconTextListCell<>(
                contact -> {
                    int unread = chatManager.unreadCountProperty(chatManager.contactKey(contact)).get();
                    String name = contact.getName();
                    return unread > 0 ? name + " (" + unread + ")" : name;
                },
                contact -> null,
                contact -> null,
                contact -> null));
        VBox.setVgrow(contactList, Priority.ALWAYS);

        VBox leftPane = new VBox(filter, contactList);
        leftPane.getStyleClass().add("chat-list-pane");

        ChatView chatView = new ChatView();
        VBox.setVgrow(chatView, Priority.ALWAYS);

        javafx.scene.control.SplitPane splitPane = new javafx.scene.control.SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.setDividerPositions(0.28);
        splitPane.getItems().addAll(leftPane, chatView);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        getChildren().add(splitPane);
        VBox.setVgrow(this, Priority.ALWAYS);

        contactList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                chatView.setConversation(null, null);
            } else {
                chatView.setConversation(
                        chatManager.contactKey(selected),
                        (key, text) -> chatManager.sendToContact(selected, text));
                chatManager.markRead(chatManager.contactKey(selected));
                contactList.refresh();
            }
        });

        chatManager.getContacts().addListener((javafx.collections.ListChangeListener<Contact>) c -> {
            contactList.refresh();
        });
    }
}
