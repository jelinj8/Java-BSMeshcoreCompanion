package cz.bliksoft.meshcorecompanion.connection;

import java.util.ArrayList;
import java.util.List;

import cz.bliksoft.javautils.app.BSApp;
import cz.bliksoft.javautils.app.exceptions.ViewableException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DeviceRegistry {

    private static final Logger log = LogManager.getLogger(DeviceRegistry.class);

    private static final String KEY_COUNT    = "connection.device.count";
    private static final String KEY_NAME     = "connection.device.%d.name";
    private static final String KEY_PUBKEY   = "connection.device.%d.pubkey";
    private static final String KEY_PORTHINT = "connection.device.%d.porthint";

    private DeviceRegistry() {}

    public static List<SavedDevice> load() {
        List<SavedDevice> result = new ArrayList<>();
        int count = intProp(KEY_COUNT, 0);
        for (int i = 0; i < count; i++) {
            String name    = strProp(String.format(KEY_NAME,     i), null);
            String pubkey  = strProp(String.format(KEY_PUBKEY,   i), null);
            String portHint = strProp(String.format(KEY_PORTHINT, i), null);
            if (name != null && pubkey != null) {
                result.add(new SavedDevice(name, pubkey, portHint));
            }
        }
        return result;
    }

    public static void save(List<SavedDevice> devices) {
        // Don't clear old entries with null — ConcurrentHashMap rejects null values.
        // Orphaned entries beyond the new count are harmless; load() uses count as limit.
        BSApp.setLocalProperty(KEY_COUNT, String.valueOf(devices.size()));
        for (int i = 0; i < devices.size(); i++) {
            BSApp.setLocalProperty(String.format(KEY_NAME,     i), devices.get(i).getName());
            BSApp.setLocalProperty(String.format(KEY_PUBKEY,   i), devices.get(i).getPubkeyHex());
            String portHint = devices.get(i).getPortHint();
            if (portHint != null) {
                BSApp.setLocalProperty(String.format(KEY_PORTHINT, i), portHint);
            }
        }
        try {
            BSApp.saveLocalProperties();
        } catch (ViewableException e) {
            log.warn("Failed to persist device registry", e);
        }
    }

    /** Add or update a device entry, keyed by pubkey hex. */
    public static void addOrUpdate(SavedDevice device) {
        List<SavedDevice> devices = load();
        devices.removeIf(d -> d.getPubkeyHex().equalsIgnoreCase(device.getPubkeyHex()));
        devices.add(device);
        save(devices);
    }

    public static void remove(SavedDevice device) {
        List<SavedDevice> devices = load();
        devices.removeIf(d -> d.getPubkeyHex().equalsIgnoreCase(device.getPubkeyHex()));
        save(devices);
    }

    private static int intProp(String key, int defaultVal) {
        Object v = BSApp.getProperty(key);
        if (v == null) return defaultVal;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private static String strProp(String key, String defaultVal) {
        Object v = BSApp.getProperty(key);
        return v != null ? v.toString() : defaultVal;
    }
}
