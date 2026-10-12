package cz.bliksoft.meshcorecompanion.settings;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.bliksoft.javautils.app.BSApp;
import cz.bliksoft.javautils.xmlfilesystem.FileObject;
import cz.bliksoft.javautils.xmlfilesystem.FileSystem;

/**
 * Official MeshCore radio presets ({@code config.suggested_radio_settings} of the MeshCore config
 * API). The list is the last download ({@code data/radio-presets.json}) when present, otherwise the
 * copy bundled with the app. The source URL is {@code /core/radio/presets@url} of the XML
 * filesystem, so config imports can override it.
 */
final class RadioPresets {

	private static final Logger log = LogManager.getLogger(RadioPresets.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	static final String DEFAULT_URL = "https://api.meshcore.nz/api/v1/config";
	private static final String BUNDLED = "/cz/bliksoft/meshcorecompanion/radio-presets.json";
	private static final File CACHE_FILE = new File("data/radio-presets.json");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	/**
	 * @param pathHashSize bytes per hop hash (1-3), {@code null} = not part of the preset
	 */
	record Preset(String title, String description, long freqHz, long bwHz, int sf, int cr, Integer pathHashSize) {
	}

	/**
	 * @param source where the list came from, for display ("bundled", "downloaded 2026-10-12 14:00")
	 */
	record PresetList(List<Preset> presets, String source) {
	}

	private RadioPresets() {
	}

	static String getUrl() {
		FileObject f = FileSystem.getFile(BSApp.CORE_CONFIG_FOLDER, "radio", "presets");
		String url = f != null ? f.getAttribute("url", null) : null;
		return url == null || url.isBlank() ? DEFAULT_URL : url.strip();
	}

	/** The downloaded list when usable, otherwise the bundled one. */
	static PresetList load() {
		if (CACHE_FILE.isFile()) {
			try (InputStream is = Files.newInputStream(CACHE_FILE.toPath())) {
				List<Preset> presets = parse(is);
				if (!presets.isEmpty())
					return new PresetList(presets,
							"downloaded " + DATE_FMT.format(Instant.ofEpochMilli(CACHE_FILE.lastModified())));
			} catch (IOException e) {
				log.warn("Failed to load radio presets from {}, using the bundled ones", CACHE_FILE, e);
			}
		}
		try (InputStream is = RadioPresets.class.getResourceAsStream(BUNDLED)) {
			if (is == null) {
				log.error("Bundled radio presets {} missing", BUNDLED);
				return new PresetList(List.of(), "none");
			}
			return new PresetList(parse(is), "bundled");
		} catch (IOException e) {
			log.error("Failed to load bundled radio presets", e);
			return new PresetList(List.of(), "none");
		}
	}

	/**
	 * Downloads the current list and stores it to {@code data/radio-presets.json}. Blocking - call
	 * off the FX thread. The stored file is left untouched when the download is not a usable list.
	 */
	static PresetList refresh() throws IOException, InterruptedException {
		String url = getUrl();
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
		HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
		if (resp.statusCode() != 200)
			throw new IOException("HTTP " + resp.statusCode() + " from " + url);

		byte[] body = resp.body();
		List<Preset> presets = parse(new ByteArrayInputStream(body));
		if (presets.isEmpty())
			throw new IOException("No radio presets in the response from " + url);

		CACHE_FILE.getAbsoluteFile().getParentFile().mkdirs();
		File tmp = new File(CACHE_FILE.getPath() + ".tmp");
		Files.write(tmp.toPath(), body);
		Files.move(tmp.toPath(), CACHE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
		log.info("Downloaded {} radio presets from {}", presets.size(), url);
		return new PresetList(presets, "downloaded " + DATE_FMT.format(Instant.now()));
	}

	static List<Preset> parse(InputStream is) throws IOException {
		JsonNode entries = mapper.readTree(is).path("config").path("suggested_radio_settings").path("entries");
		List<Preset> result = new ArrayList<>();
		for (JsonNode e : entries) {
			try {
				JsonNode hash = e.path("network_settings").path("path_hash_size");
				result.add(new Preset(e.path("title").asText(), e.path("description").asText(""),
						Math.round(Double.parseDouble(e.path("frequency").asText()) * 1_000_000),
						Math.round(Double.parseDouble(e.path("bandwidth").asText()) * 1_000),
						Integer.parseInt(e.path("spreading_factor").asText()),
						Integer.parseInt(e.path("coding_rate").asText()),
						hash.canConvertToInt() ? hash.asInt() : null));
			} catch (NumberFormatException ex) {
				log.warn("Skipping malformed radio preset {}", e);
			}
		}
		return result;
	}
}
