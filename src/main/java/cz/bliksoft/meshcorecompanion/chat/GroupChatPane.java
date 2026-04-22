package cz.bliksoft.meshcorecompanion.chat;

import cz.bliksoft.javautils.fx.controls.renderers.IconTextListCell;
import cz.bliksoft.meshcore.frames.resp.ChannelInfo;
import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GroupChatPane extends VBox {

    private final ChatManager chatManager = ChatManager.getInstance();

    public GroupChatPane() {
        TextField filter = new TextField();
        filter.setPromptText("Filter groups…");

        ListView<ChannelInfo> channelList = new ListView<>(chatManager.getChannels());
        channelList.setCellFactory(lv -> new IconTextListCell<>(
                channel -> {
                    int unread = chatManager.unreadCountProperty(chatManager.channelKey(channel)).get();
                    String name = channel.getName();
                    return unread > 0 ? name + " (" + unread + ")" : name;
                },
                channel -> null,
                channel -> null,
                channel -> null));
        VBox.setVgrow(channelList, Priority.ALWAYS);

        VBox leftPane = new VBox(filter, channelList);
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

        channelList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                chatView.setConversation(null, null);
            } else {
                chatView.setConversation(
                        chatManager.channelKey(selected),
                        (key, text) -> chatManager.sendToChannel(selected, text));
                chatManager.markRead(chatManager.channelKey(selected));
                channelList.refresh();
            }
        });

        chatManager.getChannels().addListener((javafx.collections.ListChangeListener<ChannelInfo>) c -> {
            channelList.refresh();
        });
    }
}
