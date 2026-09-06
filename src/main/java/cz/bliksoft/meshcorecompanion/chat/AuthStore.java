package cz.bliksoft.meshcorecompanion.chat;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists, per device, which ROOM/REPEATER contacts we believe we're still
 * logged in to. The remote node's own ACL is what actually remembers the login
 * (it survives the remote's reboots on its own), so this is only a local cache
 * to avoid making the user re-login after every app restart. If the remote has
 * since forgotten us (ACL evicted, factory reset, etc.) the next request
 * against it will simply fail/timeout, and the user can log in again from the
 * contact's context menu.
 */
class AuthStore {

	private static final Logger log = LogManager.getLogger(AuthStore.class);
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String BASE_PATH = "data/auth";

	private AuthStore() {
	}

	static Set<String> load(String deviceHex) {
		File file = authFile(deviceHex);
		if (!file.exists())
			return new HashSet<>();
		try {
			return new HashSet<>(mapper.readValue(file, new TypeReference<Set<String>>() {
			}));
		} catch (IOException e) {
			log.warn("Failed to load authenticated contacts from {}", file, e);
			return new HashSet<>();
		}
	}

	static void save(String deviceHex, Set<String> authenticatedContacts) {
		File file = authFile(deviceHex);
		file.getParentFile().mkdirs();
		File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
		try {
			mapper.writeValue(tmp, authenticatedContacts);
			if (file.exists())
				file.delete();
			tmp.renameTo(file);
		} catch (IOException e) {
			log.error("Failed to save authenticated contacts to {}", file, e);
			tmp.delete();
		}
	}

	private static File authFile(String deviceHex) {
		return new File(BASE_PATH + "/" + deviceHex + ".json");
	}
}
