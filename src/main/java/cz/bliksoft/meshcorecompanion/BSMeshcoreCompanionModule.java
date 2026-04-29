package cz.bliksoft.meshcorecompanion;

import java.util.Optional;

import cz.bliksoft.javautils.app.BSApp;
import cz.bliksoft.javautils.app.events.AppClosedEvent;
import cz.bliksoft.javautils.app.events.TryCloseEvent;
import cz.bliksoft.javautils.app.exceptions.ViewableException;
import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.app.ui.utils.state.binders.StageStateBinder;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.events.EventListener;
import cz.bliksoft.javautils.modules.ModuleBase;
import cz.bliksoft.meshcorecompanion.chat.ChatManager;
import cz.bliksoft.meshcorecompanion.chat.MainPane;
import cz.bliksoft.meshcorecompanion.connection.ConnectionManager;
import cz.bliksoft.meshcorecompanion.events.meshcore.MeshcorePushBridge;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class BSMeshcoreCompanionModule extends ModuleBase {

	@Override
	public String getModuleName() {
		return "BSMeshcoreCompanion";
	}

	@Override
	public void install() {
		BSAppUI.pushUI(new MainPane());
		MeshcorePushBridge.getInstance().install();
		ChatManager.getInstance().install();

		Context.getRoot()
				.addEventListener(new EventListener<AppClosedEvent>(AppClosedEvent.class, "companion disconnect") {
					@Override
					public void fired(AppClosedEvent event) {
						ConnectionManager.getInstance().disconnect();
					}
				});

		Context.getRoot()
				.addEventListener(new EventListener<TryCloseEvent>(TryCloseEvent.class, "BSMeshcoreCompanion Main") {
					@Override
					public void fired(TryCloseEvent event) {
						Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
						alert.setTitle("Exit");
						alert.setHeaderText("Exit BSMeshcoreCompanion?");
						alert.initOwner(BSAppUI.getStage());
						Optional<ButtonType> result = alert.showAndWait();
						if (result.isPresent() && result.get() == ButtonType.OK) {
							StageStateBinder.save(BSAppUI.getStage(), "@main");
							try {
								BSApp.saveLocalProperties();
							} catch (ViewableException e) {
							}
						} else {
							event.blockClosing("Rejected by user");
						}
					}
				});
	}

}
