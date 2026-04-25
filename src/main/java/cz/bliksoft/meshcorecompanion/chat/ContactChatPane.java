package cz.bliksoft.meshcorecompanion.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.meshcore.frames.FrameConstants.AdvertType;
import cz.bliksoft.meshcore.frames.FrameConstants.ContactFlags;
import cz.bliksoft.meshcore.frames.push.NewAdvertPush;
import cz.bliksoft.meshcore.frames.resp.Contact;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.function.Consumer;

public class ContactChatPane extends VBox {

	private final ChatManager chatManager = ChatManager.getInstance();

	private ListView<Contact> contactList;
	private ChatView chatView;
	private Contact selectedContact;

	public ContactChatPane() {
		// ── Sorted + filtered contact list ────────────────────────────────────
		FilteredList<Contact> filteredContacts = new FilteredList<>(chatManager.getContacts());
		SortedList<Contact> sortedContacts = new SortedList<>(filteredContacts, Comparator
				.comparing((Contact c) -> !c.hasFlag(ContactFlags.FAVOURITE)).thenComparing(c -> sortKey(c.getName())));

		contactList = new ListView<>(sortedContacts);
		contactList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		contactList
				.setCellFactory(lv -> new ContactCell(chatManager, this::handleLoginLogout, this::openDetailsDialog));
		VBox.setVgrow(contactList, Priority.ALWAYS);

		// ── Discovered list ───────────────────────────────────────────────────
		ListView<NewAdvertPush> discoveredList = new ListView<>(chatManager.getDiscoveredContacts());
		discoveredList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(NewAdvertPush item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					return;
				}
				String pubPrefix = MeshcoreUtils.hex(Arrays.copyOf(item.getPubkey(), 4));
				setText(item.getName() + " [" + item.getType() + "]  " + pubPrefix);
			}
		});
		discoveredList.setPrefHeight(100);

		// ── Chat view ─────────────────────────────────────────────────────────
		chatView = new ChatView();
		VBox.setVgrow(chatView, Priority.ALWAYS);

		VBox leftPane = buildLeftPane(contactList, discoveredList, filteredContacts);

		SplitPane splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.HORIZONTAL);
		splitPane.setDividerPositions(0.28);
		splitPane.getItems().addAll(leftPane, chatView);
		SplitPane.setResizableWithParent(leftPane, false);
		VBox.setVgrow(splitPane, Priority.ALWAYS);
		getChildren().add(splitPane);
		VBox.setVgrow(this, Priority.ALWAYS);

		// ── Selection listener ────────────────────────────────────────────────
		contactList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
			selectedContact = selected;
			if (selected == null) {
				chatView.setConversation(null, null);
			} else {
				updateChatViewForContact(selected);
				chatManager.markRead(chatManager.contactKey(selected));
				contactList.refresh();
			}
		});

		chatManager.getContacts()
				.addListener((javafx.collections.ListChangeListener<Contact>) c -> contactList.refresh());

		chatManager.setOnAuthChanged(() -> {
			if (selectedContact != null)
				updateChatViewForContact(selectedContact);
			contactList.refresh();
		});
	}

	private static String sortKey(String name) {
		return name.replaceAll("[^\\p{L}\\p{N}\\s]", "").trim().toLowerCase();
	}

	private void updateChatViewForContact(Contact contact) {
		boolean isRoomRepeater = contact.getType() == AdvertType.ADV_TYPE_ROOM
				|| contact.getType() == AdvertType.ADV_TYPE_REPEATER;
		boolean authed = !isRoomRepeater || chatManager.isAuthenticated(contact);

		chatView.setConversation(chatManager.contactKey(contact),
				authed ? (key, text, mode) -> chatManager.sendToContact(contact, text, mode) : null);
		chatView.setSendEnabled(authed);
		chatManager.markRead(chatManager.contactKey(contact));
	}

	private VBox buildLeftPane(ListView<Contact> contactList, ListView<NewAdvertPush> discoveredList,
			FilteredList<Contact> filteredContacts) {
		// ── Management toolbar ────────────────────────────────────────────────
		Button addBtn = new Button("+");
		Button removeBtn = new Button("−");
		Button importBtn = new Button("↓ Import");

		addBtn.setTooltip(new Tooltip("Add contact by pubkey"));
		removeBtn.setTooltip(new Tooltip("Remove selected contact"));
		importBtn.setTooltip(new Tooltip("Import selected discovered contact"));

		removeBtn.disableProperty().bind(contactList.getSelectionModel().selectedItemProperty().isNull());
		importBtn.disableProperty().bind(discoveredList.getSelectionModel().selectedItemProperty().isNull());

		addBtn.setOnAction(e -> openAddDialog());
		removeBtn.setOnAction(e -> removeSelected(contactList));
		importBtn.setOnAction(e -> importDiscovered(discoveredList));

		ToolBar toolbar = new ToolBar(addBtn, removeBtn);

		// ── Filter bar ────────────────────────────────────────────────────────
		ChoiceBox<String> typeFilter = new ChoiceBox<>();
		typeFilter.getItems().addAll("All", "Favourites", "Chat", "Room", "Repeater");
		typeFilter.setValue("All");

		javafx.scene.control.TextField nameFilter = new javafx.scene.control.TextField();
		nameFilter.setPromptText("Search…");
		HBox.setHgrow(nameFilter, Priority.ALWAYS);

		Runnable updateFilter = () -> {
			String sel = typeFilter.getValue();
			String text = nameFilter.getText().toLowerCase().strip();
			filteredContacts.setPredicate(c -> {
				boolean typeOk = switch (sel) {
				case "Favourites" -> c.hasFlag(ContactFlags.FAVOURITE);
				case "Chat" -> c.getType() == AdvertType.ADV_TYPE_CHAT;
				case "Room" -> c.getType() == AdvertType.ADV_TYPE_ROOM;
				case "Repeater" -> c.getType() == AdvertType.ADV_TYPE_REPEATER;
				default -> true;
				};
				boolean nameOk = text.isEmpty() || c.getName().toLowerCase().contains(text);
				return typeOk && nameOk;
			});
		};
		typeFilter.setOnAction(e -> updateFilter.run());
		nameFilter.textProperty().addListener((obs, o, n) -> updateFilter.run());

		HBox filterBar = new HBox(4, typeFilter, nameFilter);
		filterBar.setPadding(new Insets(2, 4, 2, 4));

		Label savedLabel = new Label("Contacts");
		savedLabel.setPadding(new Insets(2, 4, 0, 4));

		Label discoveredLabel = new Label("Discovered");
		discoveredLabel.setPadding(new Insets(2, 4, 0, 4));

		ToolBar discoveredToolbar = new ToolBar(importBtn);

		VBox pane = new VBox(toolbar, filterBar, savedLabel, contactList, new Separator(), discoveredLabel,
				discoveredList, discoveredToolbar);
		pane.getStyleClass().add("chat-list-pane");
		return pane;
	}

	private void handleLoginLogout(Contact contact) {
		if (contact == null)
			return;
		if (chatManager.isAuthenticated(contact)) {
			chatManager.logoutFromRoom(contact);
		} else {
			openLoginDialog(contact);
		}
	}

	private void openAddDialog() {
		TextInputDialog pubkeyDlg = new TextInputDialog();
		pubkeyDlg.setTitle("Add contact");
		pubkeyDlg.setHeaderText("Add contact by public key");
		pubkeyDlg.setContentText("Public key (hex):");
		Optional<String> pubkey = pubkeyDlg.showAndWait();
		if (pubkey.isEmpty() || pubkey.get().isBlank())
			return;

		TextInputDialog nameDlg = new TextInputDialog();
		nameDlg.setTitle("Add contact");
		nameDlg.setHeaderText("Set a display name for this contact");
		nameDlg.setContentText("Name:");
		Optional<String> name = nameDlg.showAndWait();
		if (name.isEmpty() || name.get().isBlank())
			return;

		try {
			chatManager.addContactByPubkey(pubkey.get().strip(), name.get().strip());
		} catch (IllegalArgumentException ex) {
			Alert err = new Alert(Alert.AlertType.ERROR);
			err.setTitle("Invalid key");
			err.setHeaderText("The public key is not valid hex.");
			err.showAndWait();
		}
	}

	private void removeSelected(ListView<Contact> list) {
		Contact selected = list.getSelectionModel().getSelectedItem();
		if (selected == null)
			return;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Remove contact");
		confirm.setHeaderText("Remove " + selected.getName() + "?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.OK)
				chatManager.removeContact(selected);
		});
	}

	private void openLoginDialog(Contact contact) {
		if (contact == null)
			return;
		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);
		PasswordField pw = new PasswordField();
		grid.add(new Label("Password:"), 0, 0);
		grid.add(pw, 1, 0);

		Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
		dlg.setTitle("Login");
		dlg.setHeaderText("Log in to " + contact.getName());
		dlg.getDialogPane().setContent(grid);
		dlg.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.OK)
				chatManager.loginToRoom(contact, pw.getText());
		});
	}

	private void openDetailsDialog(Contact contact) {
		if (contact == null)
			return;

		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(6);
		grid.setPadding(new Insets(8));

		int row = 0;

		// Name
		grid.add(new Label("Name:"), 0, row);
		grid.add(new Label(contact.getName()), 1, row++);

		// Pubkey (selectable)
		grid.add(new Label("Public key:"), 0, row);
		javafx.scene.control.TextField pubkeyField = new javafx.scene.control.TextField(
				MeshcoreUtils.hex(contact.getPubkey()));
		pubkeyField.setEditable(false);
		pubkeyField.setPrefColumnCount(32);
		grid.add(pubkeyField, 1, row++);

		// Type
		grid.add(new Label("Type:"), 0, row);
		grid.add(new Label(contact.getType().name()), 1, row++);

		// Favourite
		grid.add(new Label("Favourite:"), 0, row);
		grid.add(new Label(contact.hasFlag(ContactFlags.FAVOURITE) ? "Yes" : "No"), 1, row++);

		// Last advert
		grid.add(new Label("Last seen:"), 0, row);
		String lastSeen = contact.getAdvertTS() > 0 ? fmt.format(Instant.ofEpochSecond(contact.getAdvertTS()))
				: "Unknown";
		grid.add(new Label(lastSeen), 1, row++);

		// Path
		grid.add(new Label("Path:"), 0, row);
		String pathText;
		if (!contact.isPathKnown()) {
			pathText = "Unknown";
		} else if (contact.getPathLength() == 0) {
			pathText = "Direct";
		} else {
			byte[] out = contact.getOutPath();
			int hashLen = contact.getHashLength();
			int hops = contact.getPathLength();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < hops * hashLen && i + hashLen <= out.length; i += hashLen) {
				if (sb.length() > 0)
					sb.append(" → ");
				sb.append(MeshcoreUtils.hex(Arrays.copyOfRange(out, i, i + hashLen)));
			}
			pathText = hops + " hop(s): " + sb;
		}
		grid.add(new Label(pathText), 1, row++);

		// Location
		Double lat = contact.getLat();
		Double lon = contact.getLon();
		if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
			grid.add(new Label("Location:"), 0, row);
			grid.add(new Label(String.format("%.6f, %.6f", lat, lon)), 1, row++);
		}

		Alert dlg = new Alert(Alert.AlertType.INFORMATION);
		dlg.setTitle("Contact details");
		dlg.setHeaderText(contact.getName());
		dlg.getDialogPane().setContent(grid);
		dlg.initOwner(BSAppUI.getStage());
		dlg.showAndWait();
	}

	private void importDiscovered(ListView<NewAdvertPush> list) {
		NewAdvertPush selected = list.getSelectionModel().getSelectedItem();
		if (selected == null)
			return;
		String pubkeyHex = MeshcoreUtils.hex(selected.getPubkey());
		chatManager.importDiscovered(selected, msg -> {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("Import failed");
			alert.setHeaderText("Could not import \"" + selected.getName() + "\".");
			alert.setContentText(msg);
			alert.initOwner(BSAppUI.getStage());
			alert.showAndWait();
		}, () -> {
			for (Contact c : contactList.getItems()) {
				if (MeshcoreUtils.hex(c.getPubkey()).equals(pubkeyHex)) {
					contactList.getSelectionModel().select(c);
					contactList.scrollTo(c);
					return;
				}
			}
		});
	}

	// ── Contact cell ──────────────────────────────────────────────────────────

	private static class ContactCell extends javafx.scene.control.ListCell<Contact> {
		private final ChatManager mgr;
		private final Consumer<Contact> onLoginLogout;
		private final Consumer<Contact> onDetails;

		private Contact currentContact;
		private javafx.beans.property.IntegerProperty observedProp;
		private javafx.beans.InvalidationListener unreadListener;

		ContactCell(ChatManager mgr, Consumer<Contact> onLoginLogout, Consumer<Contact> onDetails) {
			this.mgr = mgr;
			this.onLoginLogout = onLoginLogout;
			this.onDetails = onDetails;
		}

		@Override
		protected void updateItem(Contact contact, boolean empty) {
			super.updateItem(contact, empty);
			if (observedProp != null) {
				observedProp.removeListener(unreadListener);
				observedProp = null;
				unreadListener = null;
			}
			currentContact = contact;
			if (empty || contact == null) {
				setText(null);
				setContextMenu(null);
				return;
			}
			observedProp = mgr.unreadCountProperty(mgr.contactKey(contact));
			unreadListener = obs -> refreshText();
			observedProp.addListener(unreadListener);
			refreshText();
			setContextMenu(buildContextMenu(contact));
		}

		private void refreshText() {
			Contact c = currentContact;
			if (c == null)
				return;
			int unread = mgr.unreadCountProperty(mgr.contactKey(c)).get();
			boolean fav = c.hasFlag(ContactFlags.FAVOURITE);
			setText((fav ? "★ " : "") + c.getName() + (unread > 0 ? " (" + unread + ")" : ""));
		}

		private ContextMenu buildContextMenu(Contact contact) {
			boolean fav = contact.hasFlag(ContactFlags.FAVOURITE);
			MenuItem favItem = new MenuItem(fav ? "Unset favourite" : "Set as favourite");
			favItem.setOnAction(e -> mgr.toggleFavourite(contact));

			boolean isRoomRepeater = contact.getType() == AdvertType.ADV_TYPE_ROOM
					|| contact.getType() == AdvertType.ADV_TYPE_REPEATER;
			MenuItem loginItem = new MenuItem(isRoomRepeater && mgr.isAuthenticated(contact) ? "Logout" : "Login…");
			loginItem.setVisible(isRoomRepeater);
			loginItem.setOnAction(e -> onLoginLogout.accept(contact));

			MenuItem resetPathItem = new MenuItem("Reset path");
			resetPathItem.setDisable(!mgr.isConnected());
			resetPathItem.setOnAction(e -> mgr.resetPath(contact));

			MenuItem detailsItem = new MenuItem("Show details");
			detailsItem.setOnAction(e -> onDetails.accept(contact));

			ContextMenu menu = new ContextMenu(favItem);
			if (isRoomRepeater)
				menu.getItems().add(loginItem);
			menu.getItems().addAll(new SeparatorMenuItem(), resetPathItem, new SeparatorMenuItem(), detailsItem);
			return menu;
		}
	}
}
