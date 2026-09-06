package cz.bliksoft.meshcorecompanion.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the CayenneLPP-encoded {@code channel, type, value...} tuples used by
 * Meshcore firmware for {@code REQ_TYPE_GET_TELEMETRY_DATA} responses.
 */
final class TelemetryDecoder {

	private TelemetryDecoder() {
	}

	static String decode(byte[] data) {
		List<String> lines = new ArrayList<>();
		int i = 0;
		while (i + 2 <= data.length) {
			int channel = data[i] & 0xFF;
			if (channel == 0) {
				// Trailing zero-padding: mesh packet payloads are AES-encrypted in fixed
				// 16-byte blocks with zero-fill, so the decrypted plaintext is padded with
				// zero bytes up to the next block boundary. Firmware never uses channel 0
				// for real telemetry (it always starts at TELEM_CHANNEL_SELF = 1), so this
				// reliably marks the end of the actual data.
				break;
			}
			i++;
			int type = data[i++] & 0xFF;
			int remaining = data.length - i;
			String value;
			switch (type) {
			case 2, 3 -> { // analog input/output: 2 bytes signed, /100
				if (remaining < 2) {
					value = null;
					break;
				}
				value = String.format("%.2f", readSigned(data, i, 2) / 100.0);
				i += 2;
			}
			case 100 -> { // generic sensor: 4 bytes unsigned
				if (remaining < 4) {
					value = null;
					break;
				}
				value = String.valueOf(readUnsigned(data, i, 4));
				i += 4;
			}
			case 101 -> { // luminosity: 2 bytes unsigned, lux
				if (remaining < 2) {
					value = null;
					break;
				}
				value = readUnsigned(data, i, 2) + " lux";
				i += 2;
			}
			case 102 -> { // presence: 1 byte
				if (remaining < 1) {
					value = null;
					break;
				}
				value = "presence=" + (data[i] & 0xFF);
				i += 1;
			}
			case 103 -> { // temperature: 2 bytes signed, /10, degC
				if (remaining < 2) {
					value = null;
					break;
				}
				value = String.format("%.1f °C", readSigned(data, i, 2) / 10.0);
				i += 2;
			}
			case 104 -> { // relative humidity: 1 byte unsigned, /2, %
				if (remaining < 1) {
					value = null;
					break;
				}
				value = String.format("%.1f %% RH", (data[i] & 0xFF) / 2.0);
				i += 1;
			}
			case 115 -> { // barometric pressure: 2 bytes unsigned, /10, hPa
				if (remaining < 2) {
					value = null;
					break;
				}
				value = String.format("%.1f hPa", readUnsigned(data, i, 2) / 10.0);
				i += 2;
			}
			case 116 -> { // voltage: 2 bytes unsigned, /100, V
				if (remaining < 2) {
					value = null;
					break;
				}
				value = String.format("%.2f V", readUnsigned(data, i, 2) / 100.0);
				i += 2;
			}
			case 117 -> { // current: 2 bytes unsigned, /1000, A
				if (remaining < 2) {
					value = null;
					break;
				}
				value = String.format("%.3f A", readUnsigned(data, i, 2) / 1000.0);
				i += 2;
			}
			case 120 -> { // percentage: 1 byte unsigned, %
				if (remaining < 1) {
					value = null;
					break;
				}
				value = (data[i] & 0xFF) + " %";
				i += 1;
			}
			case 133 -> { // unix time: 4 bytes unsigned
				if (remaining < 4) {
					value = null;
					break;
				}
				value = "time=" + readUnsigned(data, i, 4);
				i += 4;
			}
			case 136 -> { // GPS: 3+3+3 bytes signed, lat/lon /10000, alt /100
				if (remaining < 9) {
					value = null;
					break;
				}
				double lat = readSigned(data, i, 3) / 10000.0;
				double lon = readSigned(data, i + 3, 3) / 10000.0;
				double alt = readSigned(data, i + 6, 3) / 100.0;
				value = String.format("%.5f, %.5f  alt %.1f m", lat, lon, alt);
				i += 9;
			}
			default -> {
				// unknown type: length is unknown, can't safely keep parsing
				value = "unknown type " + type + " (rest of payload skipped)";
				i = data.length;
			}
			}
			if (value == null) {
				lines.add("Ch" + channel + ": (truncated)");
				break;
			}
			lines.add("Ch" + channel + ": " + value);
		}
		return lines.isEmpty() ? "(no telemetry data)" : String.join("\n", lines);
	}

	private static long readUnsigned(byte[] data, int offset, int len) {
		long v = 0;
		for (int j = 0; j < len; j++)
			v = (v << 8) | (data[offset + j] & 0xFF);
		return v;
	}

	private static long readSigned(byte[] data, int offset, int len) {
		long v = readUnsigned(data, offset, len);
		long signBit = 1L << (len * 8 - 1);
		if ((v & signBit) != 0)
			v -= (signBit << 1);
		return v;
	}
}
