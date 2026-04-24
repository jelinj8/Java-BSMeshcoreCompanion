package cz.bliksoft.meshcorecompanion.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.context.AbstractContextListener;
import cz.bliksoft.javautils.context.Context;
import cz.bliksoft.javautils.context.ContextChangedEvent;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.frames.resp.SelfInfo;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class RadioConfigSection extends VBox {

    private static final Logger log = LogManager.getLogger(RadioConfigSection.class);

    private static final long[] BW_OPTIONS_HZ = {125000, 250000, 500000};
    private static final String[] BW_LABELS   = {"125 kHz", "250 kHz", "500 kHz"};

    private final TextField nodeNameField = new TextField();
    private final TextField freqField = new TextField();
    private final ChoiceBox<String> bwBox = new ChoiceBox<>();
    private final Spinner<Integer> sfSpinner = new Spinner<>(7, 12, 7);
    private final Spinner<Integer> crSpinner = new Spinner<>(5, 8, 5);
    private final Spinner<Integer> txPowerSpinner;
    private final CheckBox repeatBox = new CheckBox("Client repeat");
    private final Label statusLabel = new Label("Not connected");
    private final Button readBtn = new Button("Read from device");

    private final ReadOnlyBooleanWrapper connected = new ReadOnlyBooleanWrapper(false);

    private MeshcoreCompanion currentCompanion;
    private Runnable onModified;

    private int maxTxPower = 22;

    RadioConfigSection(Runnable onModified) {
        this.onModified = onModified;

        setPadding(new Insets(0));
        setSpacing(8);

        txPowerSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxTxPower, 0));
        txPowerSpinner.setEditable(true);

        bwBox.getItems().addAll(BW_LABELS);

        GridPane grid = buildGrid();

        readBtn.setOnAction(e -> readFromDevice());
        HBox btnBar = new HBox(readBtn);

        getChildren().addAll(new Label("Radio configuration:"), statusLabel, grid, btnBar);
        setDisable(true);

        wireDirtyListeners();

        Context.getCurrentContext().addContextListener(
                new AbstractContextListener<MeshcoreCompanion>(MeshcoreCompanion.class, "RadioConfigSection") {
                    @Override
                    public void fired(ContextChangedEvent<MeshcoreCompanion> event) {
                        currentCompanion = event.getNewValue();
                        Platform.runLater(() -> onCompanionChanged(currentCompanion));
                    }
                });

        // Handle already-connected state when settings is opened mid-session
        var search = Context.getCurrentContext().getValue(MeshcoreCompanion.class);
        if (search.isValid() && search.getResult() instanceof MeshcoreCompanion existing) {
            currentCompanion = existing;
            Platform.runLater(() -> onCompanionChanged(currentCompanion));
        }
    }

    private GridPane buildGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);

        int row = 0;
        grid.add(new Label("Node name:"), 0, row);
        grid.add(nodeNameField, 1, row++);

        grid.add(new Label("Frequency (MHz):"), 0, row);
        grid.add(freqField, 1, row++);

        grid.add(new Label("Bandwidth:"), 0, row);
        grid.add(bwBox, 1, row++);

        grid.add(new Label("Spreading factor:"), 0, row);
        sfSpinner.setEditable(true);
        grid.add(sfSpinner, 1, row++);

        grid.add(new Label("Coding rate (4/N):"), 0, row);
        crSpinner.setEditable(true);
        grid.add(crSpinner, 1, row++);

        grid.add(new Label("TX power (dBm):"), 0, row);
        grid.add(txPowerSpinner, 1, row++);

        grid.add(repeatBox, 1, row++);

        return grid;
    }

    private void wireDirtyListeners() {
        nodeNameField.textProperty().addListener((obs, o, n) -> onModified.run());
        freqField.textProperty().addListener((obs, o, n) -> onModified.run());
        bwBox.valueProperty().addListener((obs, o, n) -> onModified.run());
        sfSpinner.valueProperty().addListener((obs, o, n) -> onModified.run());
        crSpinner.valueProperty().addListener((obs, o, n) -> onModified.run());
        txPowerSpinner.valueProperty().addListener((obs, o, n) -> onModified.run());
        repeatBox.selectedProperty().addListener((obs, o, n) -> onModified.run());
    }

    ReadOnlyBooleanProperty connectedProperty() { return connected.getReadOnlyProperty(); }

    private void onCompanionChanged(MeshcoreCompanion companion) {
        if (companion == null) {
            connected.set(false);
            setDisable(true);
            statusLabel.setText("Not connected");
            return;
        }
        connected.set(true);
        setDisable(false);
        statusLabel.setText("Connected — click Read to load current values");
        // auto-read on connect
        readFromDevice();
    }

    private void readFromDevice() {
        MeshcoreCompanion c = currentCompanion;
        if (c == null) return;
        readBtn.setDisable(true);
        statusLabel.setText("Reading…");
        new Thread(() -> {
            try {
                SelfInfo si = c.getConfig().getSelfInfo();
                if (si == null) {
                    c.refreshSelfInfo();
                    Thread.sleep(500);
                    si = c.getConfig().getSelfInfo();
                }
                boolean repeat = c.getConfig().getDeviceInfo() != null
                        && c.getConfig().getDeviceInfo().isClientRepeat();
                SelfInfo finalSi = si;
                Platform.runLater(() -> populate(finalSi, repeat));
            } catch (Exception e) {
                log.warn("Failed to read radio config", e);
                Platform.runLater(() -> statusLabel.setText("Read failed: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> readBtn.setDisable(false));
            }
        }, "radio-read").start();
    }

    private void populate(SelfInfo si, boolean repeat) {
        if (si == null) {
            statusLabel.setText("No device info available");
            return;
        }
        // suppress dirty notifications while populating
        Runnable saved = onModified;
        onModified = () -> {};

        nodeNameField.setText(si.getNodeName());
        freqField.setText(String.format("%.4f", si.getFreq() / 1_000_000.0));

        long bw = si.getBw();
        String bwLabel = BW_LABELS[0];
        for (int i = 0; i < BW_OPTIONS_HZ.length; i++) {
            if (BW_OPTIONS_HZ[i] == bw) { bwLabel = BW_LABELS[i]; break; }
        }
        bwBox.setValue(bwLabel);

        sfSpinner.getValueFactory().setValue(si.getSf());
        crSpinner.getValueFactory().setValue(si.getCr());

        maxTxPower = si.getMaxLoraPowerDbm();
        ((SpinnerValueFactory.IntegerSpinnerValueFactory) txPowerSpinner.getValueFactory()).setMax(maxTxPower);
        txPowerSpinner.getValueFactory().setValue(si.getTxPowerDbm());

        repeatBox.setSelected(repeat);

        statusLabel.setText("Loaded from device");
        onModified = saved;
    }

    void writeToDevice() throws Exception {
        MeshcoreCompanion c = currentCompanion;
        if (c == null) return;

        String name = nodeNameField.getText().strip();
        if (!name.isEmpty()) {
            c.getConfig().setAdvertName(name);
        }

        double freqMhz = Double.parseDouble(freqField.getText().strip());
        long freq = Math.round(freqMhz * 1_000_000);
        int bwIdx = bwBox.getItems().indexOf(bwBox.getValue());
        long bw = bwIdx >= 0 ? BW_OPTIONS_HZ[bwIdx] : BW_OPTIONS_HZ[0];
        byte sf = sfSpinner.getValue().byteValue();
        byte cr = crSpinner.getValue().byteValue();
        boolean repeat = repeatBox.isSelected();
        c.getConfig().setRadioParams(freq, bw, sf, cr, repeat);

        byte txPower = txPowerSpinner.getValue().byteValue();
        c.getConfig().setRadioTxPower(txPower);
    }

    boolean isConnected() {
        return currentCompanion != null;
    }

    MeshcoreCompanion getCompanion() {
        return currentCompanion;
    }
}
