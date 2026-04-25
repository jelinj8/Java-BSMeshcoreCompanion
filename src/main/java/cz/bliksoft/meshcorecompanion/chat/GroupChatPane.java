package cz.bliksoft.meshcorecompanion.chat;

import java.util.Optional;

import cz.bliksoft.meshcore.frames.resp.ChannelInfo;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GroupChatPane extends VBox {

	private final ChatManager chatManager = ChatManager.getInstance();

	public GroupChatPane() {
		ListView<ChannelInfo> channelList = new ListView<>(chatManager.getChannels());
		channelList.setCellFactory(lv -> new ChannelCell(chatManager, this::clearChannelHistory));
		VBox.setVgrow(channelList, Priority.ALWAYS);

		VBox leftPane = buildLeftPane(channelList);

		ChatView chatView = new ChatView();
		chatView.setModeVisible(false);
		VBox.setVgrow(chatView, Priority.ALWAYS);

		SplitPane splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.HORIZONTAL);
		splitPane.setDividerPositions(0.28);
		splitPane.getItems().addAll(leftPane, chatView);
		SplitPane.setResizableWithParent(leftPane, false);
		VBox.setVgrow(splitPane, Priority.ALWAYS);

		getChildren().add(splitPane);
		VBox.setVgrow(this, Priority.ALWAYS);

		channelList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
			if (selected == null) {
				chatView.setConversation(null, null);
			} else {
				chatView.setConversation(chatManager.channelKey(selected),
						(key, text, mode, onComplete, onTextReturn) -> {
							onComplete.run();
							chatManager.sendToChannel(selected, text);
						});
				chatManager.markRead(chatManager.channelKey(selected));
				channelList.refresh();
			}
		});

		chatManager.getChannels()
				.addListener((javafx.collections.ListChangeListener<ChannelInfo>) c -> channelList.refresh());
	}

	private VBox buildLeftPane(ListView<ChannelInfo> channelList) {
		Button addBtn = new Button("+");
		Button removeBtn = new Button("−");

		addBtn.setTooltip(new Tooltip("Add channel"));
		removeBtn.setTooltip(new Tooltip("Remove selected channel"));
		removeBtn.disableProperty().bind(channelList.getSelectionModel().selectedItemProperty().isNull());

		addBtn.setOnAction(e -> openAddDialog());
		removeBtn.setOnAction(e -> removeSelected(channelList));

		ToolBar toolbar = new ToolBar(addBtn, removeBtn);

		Label label = new Label("Channels");
		label.setPadding(new Insets(2, 4, 0, 4));

		VBox pane = new VBox(toolbar, label, channelList);
		pane.getStyleClass().add("chat-list-pane");
		return pane;
	}

	private void openAddDialog() {
		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(8);

		ChoiceBox<String> typeBox = new ChoiceBox<>();
		typeBox.getItems().addAll("Public", "Hash (#name)", "Private");
		typeBox.setValue("Public");

		TextField nameField = new TextField();
		PasswordField keyField = new PasswordField();
		keyField.setPromptText("16-byte key (hex, 32 chars)");
		keyField.setDisable(true);

		typeBox.valueProperty().addListener((obs, o, n) -> keyField.setDisable(!"Private".equals(n)));

		grid.add(new Label("Type:"), 0, 0);
		grid.add(typeBox, 1, 0);
		grid.add(new Label("Name:"), 0, 1);
		grid.add(nameField, 1, 1);
		grid.add(new Label("Key (hex):"), 0, 2);
		grid.add(buildPasswordRow(keyField), 1, 2);

		Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
		dlg.setTitle("Add channel");
		dlg.setHeaderText("Add a new channel");
		dlg.getDialogPane().setContent(grid);

		Optional<ButtonType> result = dlg.showAndWait();
		if (result.isEmpty() || result.get() != ButtonType.OK)
			return;

		String name = nameField.getText().strip();
		if (name.isEmpty())
			return;

		String type = typeBox.getValue();
		switch (type) {
		case "Public" -> chatManager.addPublicChannel(name);
		case "Hash (#name)" -> chatManager.addHashChannel(name);
		case "Private" -> {
			String keyHex = keyField.getText().strip();
			if (keyHex.length() != 32) {
				Alert err = new Alert(Alert.AlertType.ERROR);
				err.setTitle("Invalid key");
				err.setHeaderText("Private channel key must be exactly 32 hex characters (16 bytes).");
				err.showAndWait();
				return;
			}
			try {
				byte[] key = cz.bliksoft.meshcore.utils.MeshcoreUtils.fromHex(keyHex);
				chatManager.addPrivateChannel(name, key);
			} catch (IllegalArgumentException ex) {
				Alert err = new Alert(Alert.AlertType.ERROR);
				err.setTitle("Invalid key");
				err.setHeaderText("Key is not valid hex.");
				err.showAndWait();
			}
		}
		}
	}

	private HBox buildPasswordRow(PasswordField pf) {
		TextField tf = new TextField();
		tf.setManaged(false);
		tf.setVisible(false);
		// keep both fields in sync
		pf.textProperty().addListener((o, old, n) -> {
			if (!tf.getText().equals(n))
				tf.setText(n);
		});
		tf.textProperty().addListener((o, old, n) -> {
			if (!pf.getText().equals(n))
				pf.setText(n);
		});
		Button eye = new Button("👁");
		eye.setOnAction(e -> {
			boolean show = !tf.isVisible();
			tf.setVisible(show);
			tf.setManaged(show);
			pf.setVisible(!show);
			pf.setManaged(!show);
			(show ? tf : pf).requestFocus();
		});
		HBox row = new HBox(4, pf, tf, eye);
		HBox.setHgrow(pf, Priority.ALWAYS);
		HBox.setHgrow(tf, Priority.ALWAYS);
		return row;
	}

	private static class ChannelCell extends javafx.scene.control.ListCell<ChannelInfo> {
		private final ChatManager mgr;
		private final java.util.function.Consumer<ChannelInfo> onClearHistory;
		private ChannelInfo currentItem;
		private javafx.beans.property.IntegerProperty observedProp;
		private javafx.beans.InvalidationListener unreadListener;

		ChannelCell(ChatManager mgr, java.util.function.Consumer<ChannelInfo> onClearHistory) {
			this.mgr = mgr;
			this.onClearHistory = onClearHistory;
		}

		@Override
		protected void updateItem(ChannelInfo item, boolean empty) {
			super.updateItem(item, empty);
			if (observedProp != null) {
				observedProp.removeListener(unreadListener);
				observedProp = null;
				unreadListener = null;
			}
			currentItem = item;
			if (empty || item == null) {
				setText(null);
				setContextMenu(null);
				return;
			}
			observedProp = mgr.unreadCountProperty(mgr.channelKey(item));
			unreadListener = obs -> refreshText();
			observedProp.addListener(unreadListener);
			refreshText();
			javafx.scene.control.MenuItem clearItem = new javafx.scene.control.MenuItem("Clear history");
			clearItem.setOnAction(e -> onClearHistory.accept(item));
			setContextMenu(new javafx.scene.control.ContextMenu(clearItem));
		}

		private void refreshText() {
			ChannelInfo item = currentItem;
			if (item == null)
				return;
			int unread = mgr.unreadCountProperty(mgr.channelKey(item)).get();
			setText(unread > 0 ? item.getName() + " (" + unread + ")" : item.getName());
		}
	}

	private void clearChannelHistory(ChannelInfo channel) {
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Clear history");
		confirm.setHeaderText("Clear chat history for " + channel.getName() + "?");
		confirm.setContentText("This cannot be undone.");
		confirm.initOwner(cz.bliksoft.javautils.app.ui.BSAppUI.getStage());
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.OK)
				chatManager.clearHistory(chatManager.channelKey(channel));
		});
	}

	private void removeSelected(ListView<ChannelInfo> list) {
		ChannelInfo selected = list.getSelectionModel().getSelectedItem();
		if (selected == null)
			return;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
		confirm.setTitle("Remove channel");
		confirm.setHeaderText("Remove channel \"" + selected.getName() + "\"?");
		confirm.showAndWait().ifPresent(btn -> {
			if (btn == ButtonType.OK)
				chatManager.removeChannel(selected);
		});
	}
}
