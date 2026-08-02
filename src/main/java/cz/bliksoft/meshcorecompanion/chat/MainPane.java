package cz.bliksoft.meshcorecompanion.chat;

import cz.bliksoft.javautils.app.ui.actions.IconBinder;
import cz.bliksoft.javautils.fx.tools.IconspecUtils;
import cz.bliksoft.javautils.fx.tools.ImageUtils;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class MainPane extends TabPane {

	public MainPane() {
		setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

		Tab contactsTab = new Tab("Contact Chats", new ContactChatPane());
		Tab groupsTab = new Tab("Group Chats", new GroupChatPane());
		Tab logTab = new Tab("Log", new LogPane());

		double tabIconSize = IconspecUtils.getIconspecSize("tab-icon-size", 16);
		contactsTab.setGraphic(sizedIcon("tab/contact-chats", tabIconSize));
		groupsTab.setGraphic(sizedIcon("tab/group-chats", tabIconSize));
		logTab.setGraphic(sizedIcon("tab/log", tabIconSize));

		getTabs().addAll(contactsTab, groupsTab, logTab);

		ChatManager mgr = ChatManager.getInstance();
		mgr.totalContactUnreadProperty().addListener((obs, o, n) -> {
			int t = n.intValue();
			contactsTab.setText(t > 0 ? "Contact Chats (" + t + ")" : "Contact Chats");
		});
		mgr.totalGroupUnreadProperty().addListener((obs, o, n) -> {
			int t = n.intValue();
			groupsTab.setText(t > 0 ? "Group Chats (" + t + ")" : "Group Chats");
		});
	}

	private static Node sizedIcon(String iconKey, double sizePx) {
		Node icon = ImageUtils.getIconNode(IconspecUtils.getIconspec(iconKey));
		IconBinder.enforceIconSize(icon, sizePx);
		return icon;
	}
}
