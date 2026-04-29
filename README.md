# BSMeshcoreCompanion

Open-source desktop companion app for [Meshcore](https://github.com/ripplebiz/MeshCore) — a LoRa-based mesh radio firmware. Connect to your Meshcore device over USB, BLE, or TCP and chat with contacts and groups directly from your PC.

## Features

- **Contact chat** — direct messages to contacts; unread indicators, favourites, add by pubkey or from discovered adverts, login for ROOM/REPEATER nodes
- **Group chat** — Public, Hash (`#name`), and Private groups; unread indicators, add/remove
- **Send modes** — Async, Sync (with delivery confirmation), Retry with optional flood fallback
- **Signal info** — received SNR/RSSI shown per message (from paired `LOG_RX_DATA` frames)
- **Message persistence** — chat history saved per device and conversation, survives restarts
- **Settings** — radio config (frequency, bandwidth, spread factor, TX power, …), theme (light/dark/system), log history size
- **Backup / restore** — device settings, channels, and contacts; selective restore via checkboxes
- **Log window** — live stream of all incoming PUSH frames with timestamps and parsed content
- **Connection management** — save named devices, quick-reconnect, forget

## Screenshots

*(coming soon)*

## Requirements

- **Java 21** or newer ([Adoptium](https://adoptium.net/) recommended)
- Windows 10+, Linux, or macOS

No additional installation needed — JavaFX and all dependencies are bundled in the distribution zip.

## Running

1. Download and unzip the latest release
2. Launch the app:

| Platform | Command |
|---|---|
| Windows | double-click `run.bat` |
| Linux | `./run.sh` |
| macOS | `./run-mac.sh` |

On first run, the app creates a `log/` directory next to the scripts and a config folder in your home directory (`~/.BSMeshcoreCompanion/`).

## Connection types

| Transport | Notes |
|---|---|
| USB / Serial | COM port enumeration via jSerialComm; works on all platforms |
| BLE | SimpleBLE; Windows and Linux |
| TCP | Direct IP connection |

Devices can be saved by name for quick reconnection.

## Building from source

```bash
# Clone
git clone https://github.com/your-org/BSMeshcoreCompanion.git
cd BSMeshcoreCompanion

# Build distributable zip
mvn package

# Output: target/bsmeshcorecompanion-desktop-<version>.zip

# Run during development (no zip needed)
mvn javafx:run
```

All dependencies are on Maven Central. The companion libraries developed alongside this project:

- [Java-Meshcore](https://github.com/jelinj8/Java-Meshcore) — Meshcore protocol implementation
- [Java-BSToolbox-jfx](https://github.com/jelinj8/Java-BSToolbox-jfx) — JavaFX application and UI framework
- [Java-BSToolbox](https://github.com/jelinj8/Java-BSToolbox) — base utilities

## Contributing

Contributions are welcome. If you're adding generic UI or utility behaviour, please consider whether it belongs in `BSToolbox-jfx` rather than this app. Protocol-level additions belong in `meshcore-companion`.

Open an issue before starting larger changes so we can align on approach.

## License

[GNU Lesser General Public License v2.1](LICENSE)
