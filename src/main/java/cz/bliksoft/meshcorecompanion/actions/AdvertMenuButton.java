package cz.bliksoft.meshcorecompanion.actions;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.cmd.CmdSendSelfAdvert;
import cz.bliksoft.meshcore.frames.cmd.CmdSendSelfAdvert.AdvertMethod;
import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

public class AdvertMenuButton extends MenuButton {

    private static final Logger log = LogManager.getLogger(AdvertMenuButton.class);

    public AdvertMenuButton() {
        setText("Advert");
        MenuItem single = new MenuItem("Single hop");
        MenuItem flood  = new MenuItem("Flood");
        getItems().addAll(single, flood);
        disableProperty().bind(ConnectionManager.getInstance().disconnectedProperty());
        single.setOnAction(e -> sendAdvert(AdvertMethod.SINGLE));
        flood.setOnAction(e  -> sendAdvert(AdvertMethod.FLOOD));
    }

    private void sendAdvert(AdvertMethod method) {
        MeshcoreCompanion c = ConnectionManager.getInstance().getCompanion();
        if (c == null) return;
        try {
            c.sendFrame(new CmdSendSelfAdvert(method));
            log.info("Sent {} advert", method);
        } catch (IOException ex) {
            log.warn("Advert send failed", ex);
        }
    }
}
