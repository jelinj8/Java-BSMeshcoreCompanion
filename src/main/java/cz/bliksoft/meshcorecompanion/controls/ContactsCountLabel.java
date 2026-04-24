package cz.bliksoft.meshcorecompanion.controls;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcorecompanion.chat.ChatManager;
import javafx.application.Platform;
import javafx.scene.control.Label;

public class ContactsCountLabel extends Label {

    private MeshcoreCompanion currentCompanion;

    public ContactsCountLabel() {
        update();

        ChatManager.getInstance().getContacts().addListener(
                (javafx.collections.ListChangeListener<cz.bliksoft.meshcore.frames.resp.Contact>) c -> update());

        Context.getCurrentContext().addContextListener(
                new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "ContactsCountLabel") {
                    @Override
                    public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
                        currentCompanion = event.getNewValue();
                        Platform.runLater(() -> update());
                    }
                });
    }

    private void update() {
        int count = ChatManager.getInstance().getContacts().size();
        if (currentCompanion == null) {
            setText("Contacts: -");
            return;
        }
        int max = currentCompanion.getConfig().getMaxContacts();
        setText(max > 0 ? "Contacts: " + count + "/" + max : "Contacts: " + count);
    }
}
