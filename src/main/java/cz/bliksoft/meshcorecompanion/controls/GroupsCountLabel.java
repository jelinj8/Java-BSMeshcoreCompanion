package cz.bliksoft.meshcorecompanion.controls;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcorecompanion.chat.ChatManager;
import javafx.application.Platform;
import javafx.scene.control.Label;

public class GroupsCountLabel extends Label {

    private MeshcoreCompanion currentCompanion;

    public GroupsCountLabel() {
        update();

        ChatManager.getInstance().getChannels().addListener(
                (javafx.collections.ListChangeListener<cz.bliksoft.meshcore.frames.resp.ChannelInfo>) c -> update());

        Context.getCurrentContext().addContextListener(
                new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "GroupsCountLabel") {
                    @Override
                    public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
                        currentCompanion = event.getNewValue();
                        Platform.runLater(() -> update());
                    }
                });
    }

    private void update() {
        int count = ChatManager.getInstance().getChannels().size();
        if (currentCompanion == null) {
            setText("Groups: -");
            return;
        }
        int max = currentCompanion.getConfig().getMaxGroupChannels();
        setText(max > 0 ? "Groups: " + count + "/" + max : "Groups: " + count);
    }
}
