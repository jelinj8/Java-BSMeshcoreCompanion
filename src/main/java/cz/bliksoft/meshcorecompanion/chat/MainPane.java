package cz.bliksoft.meshcorecompanion.chat;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class MainPane extends TabPane {

	public MainPane() {
		setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);

		Tab contactsTab = new Tab("Contact Chats", new ContactChatPane());
		Tab groupsTab = new Tab("Group Chats", new GroupChatPane());
		Tab logTab = new Tab("Log", new LogPane());

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
}
