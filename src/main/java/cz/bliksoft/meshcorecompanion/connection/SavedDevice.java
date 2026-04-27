package cz.bliksoft.meshcorecompanion.connection;

public class SavedDevice {

	private final String name;
	private final String pubkeyHex; // 12 hex chars = 6-byte prefix
	private final String portHint; // COM port for "usb", "host:port" for "tcp", may be null
	private final String transport; // "usb" or "tcp"

	public SavedDevice(String name, String pubkeyHex, String portHint) {
		this(name, pubkeyHex, portHint, "usb");
	}

	public SavedDevice(String name, String pubkeyHex, String portHint, String transport) {
		this.name = name;
		this.pubkeyHex = pubkeyHex;
		this.portHint = portHint;
		this.transport = transport != null ? transport : "usb";
	}

	public String getName() {
		return name;
	}

	public String getPubkeyHex() {
		return pubkeyHex;
	}

	public String getPortHint() {
		return portHint;
	}

	public String getTransport() {
		return transport;
	}

	@Override
	public String toString() {
		return name + "  [" + pubkeyHex + "]";
	}
}
