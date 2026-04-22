package cz.bliksoft.meshcorecompanion.chat;

import cz.bliksoft.meshcorecompanion.events.meshcore.MeshcorePushBridge;
import cz.bliksoft.meshcorecompanion.model.LogEntry;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class LogPane extends VBox {

    private static final String ALL_TYPES = "All";

    private final FilteredList<LogEntry> filteredEntries;
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final TextField textFilter = new TextField();

    public LogPane() {
        filteredEntries = new FilteredList<>(MeshcorePushBridge.getInstance().getLogEntries());

        typeFilter.getItems().add(ALL_TYPES);
        typeFilter.getSelectionModel().selectFirst();
        typeFilter.setOnAction(e -> updateFilter());

        textFilter.setPromptText("Filter text…");
        textFilter.textProperty().addListener((obs, o, n) -> updateFilter());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar filterBar = new ToolBar(
                new Label("Type:"), typeFilter,
                spacer,
                new Label("Filter:"), textFilter
        );

        ListView<LogEntry> listView = new ListView<>(filteredEntries);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(LogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    getStyleClass().removeAll("log-push", "log-other");
                    getStyleClass().add(item.getFrameType().startsWith("PUSH_") ? "log-push" : "log-other");
                }
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        MeshcorePushBridge.getInstance().getLogEntries().addListener(
                (javafx.collections.ListChangeListener<LogEntry>) change -> updateTypeFilter());

        getChildren().addAll(filterBar, listView);
        setPadding(new Insets(0));
    }

    private void updateTypeFilter() {
        for (LogEntry e : MeshcorePushBridge.getInstance().getLogEntries()) {
            if (!typeFilter.getItems().contains(e.getFrameType())) {
                typeFilter.getItems().add(e.getFrameType());
            }
        }
    }

    private void updateFilter() {
        String selectedType = typeFilter.getValue();
        String text = textFilter.getText();
        filteredEntries.setPredicate(entry -> {
            if (selectedType != null && !ALL_TYPES.equals(selectedType)
                    && !selectedType.equals(entry.getFrameType())) {
                return false;
            }
            if (text != null && !text.isBlank()
                    && !entry.getSummary().contains(text)
                    && !entry.getFrameType().contains(text)) {
                return false;
            }
            return true;
        });
    }
}
