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
Download the zip for your platform (`-win`, `-linux`, `-linux-aarch64`, `-mac`, `-mac-aarch64`):
JavaFX's native libraries differ per platform.

## Running

1. Download and unzip the latest release
2. Launch the app:

| Platform | Command |
|---|---|
| Windows | double-click `run.bat` |
| Linux, macOS | `./run.sh` |

On first run, the app creates `log/`, `data/` and a local `.BSMeshcoreCompanion/` configuration
folder next to the scripts (wherever it is started from), and the global configuration folder
`~/.BSMeshcoreCompanion/` in your home directory.

## Connection types

| Transport | Notes |
|---|---|
| USB / Serial | COM port enumeration via jSerialComm; works on all platforms |
| BLE | Via [BSToolbox-BLE](https://github.com/jelinj8/Java-BSToolbox-BLE) (a Rust sidecar on `btleplug`); verified on Windows and Linux |
| TCP | Direct IP connection |

Devices can be saved by name for quick reconnection.

Connecting to a BLE device from a scan result prompts for a pairing PIN up front (prefilled with
MeshCore's default `123456`) — leave it blank to skip pairing. It's only needed the first time you
connect to a given device; reconnecting to an already-paired device needs no PIN, since the OS
keeps the bond. **macOS:** this PIN prompt has no effect there — CoreBluetooth has no public API
for supplying a pairing PIN programmatically, a permanent platform limitation. Pair the device
through macOS's own Bluetooth settings before connecting from the app.

## Building from source

```bash
# Clone
git clone https://github.com/jelinj8/Java-BSMeshcoreCompanion.git
cd Java-BSMeshcoreCompanion

# Build distributable zip for this machine's platform
mvn package
# Output: target/bsmeshcorecompanion-desktop-<version>-<platform>.zip

# ... or for another platform (win, linux, linux-aarch64, mac, mac-aarch64)
mvn package -Djavafx.platform=linux

# ... or for all platforms at once (release)
mvn package -Pdist

# Run during development (no zip needed)
mvn javafx:run
```

All dependencies are on Maven Central. The companion libraries developed alongside this project:

- [Java-Meshcore](https://github.com/jelinj8/Java-Meshcore) — Meshcore protocol implementation
- [Java-BSToolbox-jfx](https://github.com/jelinj8/Java-BSToolbox-jfx) — JavaFX application and UI framework
- [Java-BSToolbox](https://github.com/jelinj8/Java-BSToolbox) — base utilities
- [Java-BSToolbox-BLE](https://github.com/jelinj8/Java-BSToolbox-BLE) — cross-platform BLE client (Rust sidecar on `btleplug`)

## Contributing

Contributions are welcome. If you're adding generic UI or utility behaviour, please consider whether it belongs in `BSToolbox-jfx` rather than this app. Protocol-level additions belong in `meshcore-companion`.

Open an issue before starting larger changes so we can align on approach.

## License

[GNU Lesser General Public License v2.1](LICENSE)
