package cz.bliksoft.meshcorecompanion.chat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.bliksoft.meshcorecompanion.model.ChatMessage;

class ChatStore {

	private static final Logger log = LogManager.getLogger(ChatStore.class);
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String BASE_PATH = "data/chats";

	private ChatStore() {
	}

	static List<ChatMessage> load(String deviceHex, String conversationKey) {
		File file = chatFile(deviceHex, conversationKey);
		if (!file.exists())
			return new ArrayList<>();
		try {
			return mapper.readValue(file, new TypeReference<List<ChatMessage>>() {
			});
		} catch (IOException e) {
			log.warn("Failed to load chat history from {}", file, e);
			return new ArrayList<>();
		}
	}

	static void save(String deviceHex, String conversationKey, List<ChatMessage> messages) {
		File file = chatFile(deviceHex, conversationKey);
		file.getParentFile().mkdirs();
		File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
		try {
			mapper.writeValue(tmp, messages);
			if (file.exists())
				file.delete();
			tmp.renameTo(file);
		} catch (IOException e) {
			log.error("Failed to save chat history to {}", file, e);
			tmp.delete();
		}
	}

	static void appendMessage(String deviceHex, String conversationKey, ChatMessage msg) {
		List<ChatMessage> messages = load(deviceHex, conversationKey);
		messages.add(msg);
		save(deviceHex, conversationKey, messages);
	}

	static void clear(String deviceHex, String conversationKey) {
		chatFile(deviceHex, conversationKey).delete();
	}

	static void clearAll(String deviceHex) {
		File dir = new File(BASE_PATH + "/" + deviceHex);
		if (!dir.isDirectory())
			return;
		File[] files = dir.listFiles();
		if (files == null)
			return;
		for (File f : files) {
			if (f.getName().endsWith(".json"))
				f.delete();
		}
	}

	private static File chatFile(String deviceHex, String conversationKey) {
		return new File(BASE_PATH + "/" + deviceHex + "/" + conversationKey + ".json");
	}
}
