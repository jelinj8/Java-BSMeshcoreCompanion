package cz.bliksoft.meshcorecompanion.chat;

import java.util.Arrays;
import java.util.Optional;

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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;

public class ContactChatPane extends VBox {

    private final ChatManager chatManager = ChatManager.getInstance();

    private ListView<Contact> contactList;
    private ChatView chatView;
    private Button loginBtn;
    private Contact selectedContact;

    public ContactChatPane() {
        // ── Sorted + filtered contact list ────────────────────────────────────
        FilteredList<Contact> filteredContacts = new FilteredList<>(chatManager.getContacts());
        SortedList<Contact> sortedContacts = new SortedList<>(filteredContacts,
                Comparator.comparing((Contact c) -> !c.hasFlag(ContactFlags.FAVOURITE))
                          .thenComparing(c -> sortKey(c.getName())));

        contactList = new ListView<>(sortedContacts);
        contactList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        contactList.setCellFactory(lv -> new ContactCell(chatManager));
        VBox.setVgrow(contactList, Priority.ALWAYS);

        // ── Discovered list ───────────────────────────────────────────────────
        ListView<NewAdvertPush> discoveredList = new ListView<>(chatManager.getDiscoveredContacts());
        discoveredList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(NewAdvertPush item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
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
            refreshLoginBtn();
        });

        chatManager.getContacts().addListener(
                (javafx.collections.ListChangeListener<Contact>) c -> {
                    contactList.refresh();
                    // Re-evaluate auth state if selection is still valid
                    if (selectedContact != null) refreshLoginBtn();
                });

        // Refresh login button + send state when auth changes
        chatManager.setOnAuthChanged(() -> {
            refreshLoginBtn();
            if (selectedContact != null) updateChatViewForContact(selectedContact);
        });
    }

    private static String sortKey(String name) {
        return name.replaceAll("[^\\p{L}\\p{N}\\s]", "").trim().toLowerCase();
    }

    private void updateChatViewForContact(Contact contact) {
        boolean isRoomRepeater = contact.getType() == AdvertType.ADV_TYPE_ROOM
                || contact.getType() == AdvertType.ADV_TYPE_REPEATER;
        boolean authed = !isRoomRepeater || chatManager.isAuthenticated(contact);

        chatView.setConversation(
                chatManager.contactKey(contact),
                authed ? (key, text, mode) -> chatManager.sendToContact(contact, text, mode) : null);
        chatView.setSendEnabled(authed);
        chatManager.markRead(chatManager.contactKey(contact));
    }

    private void refreshLoginBtn() {
        if (loginBtn == null) return;
        Contact selected = selectedContact;
        boolean isRoomRepeater = selected != null
                && (selected.getType() == AdvertType.ADV_TYPE_ROOM
                        || selected.getType() == AdvertType.ADV_TYPE_REPEATER);
        loginBtn.setDisable(!isRoomRepeater);
        if (isRoomRepeater && chatManager.isAuthenticated(selected)) {
            loginBtn.setText("Logout");
        } else {
            loginBtn.setText("Login…");
        }
    }

    private VBox buildLeftPane(ListView<Contact> contactList,
                               ListView<NewAdvertPush> discoveredList,
                               FilteredList<Contact> filteredContacts) {
        // ── Management toolbar ────────────────────────────────────────────────
        Button addBtn    = new Button("+");
        Button removeBtn = new Button("−");
        Button favBtn    = new Button("★");
        loginBtn         = new Button("Login…");
        Button importBtn = new Button("↓ Import");

        addBtn.setTooltip(new Tooltip("Add contact by pubkey"));
        removeBtn.setTooltip(new Tooltip("Remove selected contact"));
        favBtn.setTooltip(new Tooltip("Toggle favourite"));
        loginBtn.setTooltip(new Tooltip("Log in to / out of ROOM or REPEATER"));
        importBtn.setTooltip(new Tooltip("Import selected discovered contact"));

        removeBtn.disableProperty().bind(contactList.getSelectionModel().selectedItemProperty().isNull());
        favBtn.disableProperty().bind(contactList.getSelectionModel().selectedItemProperty().isNull());
        loginBtn.setDisable(true);
        importBtn.disableProperty().bind(discoveredList.getSelectionModel().selectedItemProperty().isNull());

        addBtn.setOnAction(e -> openAddDialog());
        removeBtn.setOnAction(e -> removeSelected(contactList));
        favBtn.setOnAction(e -> toggleFavSelected(contactList));
        loginBtn.setOnAction(e -> handleLoginLogout());
        importBtn.setOnAction(e -> importDiscovered(discoveredList));

        ToolBar toolbar = new ToolBar(addBtn, removeBtn, favBtn, new Separator(), loginBtn);

        // ── Filter bar ────────────────────────────────────────────────────────
        ChoiceBox<String> typeFilter = new ChoiceBox<>();
        typeFilter.getItems().addAll("All", "Favourites", "Chat", "Room", "Repeater");
        typeFilter.setValue("All");

        javafx.scene.control.TextField nameFilter = new javafx.scene.control.TextField();
        nameFilter.setPromptText("Search…");
        HBox.setHgrow(nameFilter, Priority.ALWAYS);

        Runnable updateFilter = () -> {
            String sel  = typeFilter.getValue();
            String text = nameFilter.getText().toLowerCase().strip();
            filteredContacts.setPredicate(c -> {
                boolean typeOk = switch (sel) {
                    case "Favourites" -> c.hasFlag(ContactFlags.FAVOURITE);
                    case "Chat"       -> c.getType() == AdvertType.ADV_TYPE_CHAT;
                    case "Room"       -> c.getType() == AdvertType.ADV_TYPE_ROOM;
                    case "Repeater"   -> c.getType() == AdvertType.ADV_TYPE_REPEATER;
                    default           -> true;
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

        VBox pane = new VBox(toolbar, filterBar, savedLabel, contactList,
                new Separator(), discoveredLabel, discoveredList, discoveredToolbar);
        pane.getStyleClass().add("chat-list-pane");
        return pane;
    }

    private void handleLoginLogout() {
        Contact selected = selectedContact;
        if (selected == null) return;
        if (chatManager.isAuthenticated(selected)) {
            chatManager.logoutFromRoom(selected);
        } else {
            openLoginDialog(selected);
        }
    }

    private void openAddDialog() {
        TextInputDialog pubkeyDlg = new TextInputDialog();
        pubkeyDlg.setTitle("Add contact");
        pubkeyDlg.setHeaderText("Add contact by public key");
        pubkeyDlg.setContentText("Public key (hex):");
        Optional<String> pubkey = pubkeyDlg.showAndWait();
        if (pubkey.isEmpty() || pubkey.get().isBlank()) return;

        TextInputDialog nameDlg = new TextInputDialog();
        nameDlg.setTitle("Add contact");
        nameDlg.setHeaderText("Set a display name for this contact");
        nameDlg.setContentText("Name:");
        Optional<String> name = nameDlg.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) return;

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
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove contact");
        confirm.setHeaderText("Remove " + selected.getName() + "?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) chatManager.removeContact(selected);
        });
    }

    private void toggleFavSelected(ListView<Contact> list) {
        Contact selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) chatManager.toggleFavourite(selected);
    }

    private void openLoginDialog(Contact contact) {
        if (contact == null) return;
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
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
            if (btn == ButtonType.OK) chatManager.loginToRoom(contact, pw.getText());
        });
    }

    private void importDiscovered(ListView<NewAdvertPush> list) {
        NewAdvertPush selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) chatManager.importDiscovered(selected);
    }

    // ── Contact cell ──────────────────────────────────────────────────────────

    private static class ContactCell extends javafx.scene.control.ListCell<Contact> {
        private final ChatManager mgr;
        ContactCell(ChatManager mgr) { this.mgr = mgr; }

        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            if (empty || contact == null) {
                setText(null);
                return;
            }
            int unread = mgr.unreadCountProperty(mgr.contactKey(contact)).get();
            boolean fav = contact.hasFlag(ContactFlags.FAVOURITE);
            String prefix = fav ? "★ " : "";
            String suffix = unread > 0 ? " (" + unread + ")" : "";
            setText(prefix + contact.getName() + suffix);
        }
    }
}
