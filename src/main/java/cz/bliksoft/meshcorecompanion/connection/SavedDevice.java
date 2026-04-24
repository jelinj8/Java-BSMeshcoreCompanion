package cz.bliksoft.meshcorecompanion.connection;

public class SavedDevice {

    private final String name;
    private final String pubkeyHex;   // 12 hex chars = 6-byte prefix
    private final String portHint;    // last known port, may be null

    public SavedDevice(String name, String pubkeyHex, String portHint) {
        this.name = name;
        this.pubkeyHex = pubkeyHex;
        this.portHint = portHint;
    }

    public String getName() { return name; }
    public String getPubkeyHex() { return pubkeyHex; }
    public String getPortHint() { return portHint; }

    @Override
    public String toString() { return name + "  [" + pubkeyHex + "]"; }
}
