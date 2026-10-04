package cz.bliksoft.meshcorecompanion.connection;

import java.io.File;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.app.BSAppJFX;
import cz.bliksoft.javautils.exceptions.ViewableException;
import cz.bliksoft.javautils.logging.LogUtils;
import cz.bliksoft.meshcore.companion.BleMeshcoreCompanion;
import cz.bliksoft.meshcore.companion.MeshcoreCompanion;
import cz.bliksoft.meshcore.companion.TCPMeshcoreCompanion;
import cz.bliksoft.meshcore.utils.MeshcoreUtils;

/**
 * Per device + transport switch of the companion traffic log (one JSON file per
 * frame / BLE sidecar message, see
 * {@link MeshcoreCompanion#setTrafficLogDir(File)}) into
 * {@code <log dir>/traffic}.
 */
public class TrafficLogging {

	private static final Logger log = LogManager.getLogger(TrafficLogging.class);

	private static final String KEY_ENABLED = "connection.trafficlog.%s.%s";

	private TrafficLogging() {
	}

	/** @return the traffic log folder, or {@code null} when there's no log dir */
	public static File dir() {
		File logDir = LogUtils.getLogDir();
		return logDir == null ? null : new File(logDir, "traffic");
	}

	public static boolean isEnabled(String pubkeyHex, String transport) {
		if (pubkeyHex == null || transport == null)
			return false;
		Object v = BSAppJFX.getProperty(key(pubkeyHex, transport));
		return v != null && Boolean.parseBoolean(v.toString());
	}

	public static void setEnabled(String pubkeyHex, String transport, boolean enabled) {
		if (enabled)
			BSAppJFX.setLocalProperty(key(pubkeyHex, transport), "true");
		else
			BSAppJFX.removeLocalProperty(key(pubkeyHex, transport));
		try {
			BSAppJFX.saveLocalProperties();
		} catch (ViewableException e) {
			log.warn("Failed to persist traffic log setting", e);
		}
	}

	/** @return the folder to log to for the given device, or {@code null} */
	public static File dirFor(String pubkeyHex, String transport) {
		return isEnabled(pubkeyHex, transport) ? dir() : null;
	}

	/**
	 * The folder for a device about to be connected, looked up by the saved
	 * device's transport + port hint (the pubkey isn't known before connecting).
	 */
	static File dirForHint(String transport, String hint) {
		if (hint == null)
			return null;
		for (SavedDevice d : DeviceRegistry.load()) {
			if (transport.equals(d.getTransport()) && hint.equalsIgnoreCase(d.getPortHint()))
				return dirFor(d.getPubkeyHex(), transport);
		}
		return null;
	}

	/** @return the 6-byte pubkey prefix hex of a connected companion, or null */
	public static String pubkeyOf(MeshcoreCompanion c) {
		var si = c == null ? null : c.getSelfInfo();
		return si == null ? null : MeshcoreUtils.hex(Arrays.copyOf(si.getPubkey(), 6));
	}

	/** @return {@code usb}, {@code tcp} or {@code ble}, as in {@link SavedDevice} */
	public static String transportOf(MeshcoreCompanion c) {
		if (c instanceof BleMeshcoreCompanion)
			return "ble";
		if (c instanceof TCPMeshcoreCompanion)
			return "tcp";
		return "usb";
	}

	/** Applies the stored setting to a freshly connected companion. */
	static void apply(MeshcoreCompanion c) {
		c.setTrafficLogDir(dirFor(pubkeyOf(c), transportOf(c)));
	}

	private static String key(String pubkeyHex, String transport) {
		return String.format(KEY_ENABLED, pubkeyHex.toLowerCase(), transport);
	}
}
