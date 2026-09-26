# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

**BSMeshcoreCompanion desktop** is a JavaFX desktop chat application for Meshcore radio. The core implementation is substantially complete.

**Implemented:**
- Main UI: tabbed pane (contact chats, group chats, log window), push-in settings pane, toolbar with connect/disconnect/settings/save/close actions
- Status bar: live contacts count/capacity, groups count/capacity
- Connection: USB/serial (`SerialMeshcoreCompanion`), BLE (`BleMeshcoreCompanion`), TCP (`TCPMeshcoreCompanion`); saved device registry; connect dialog
- Contact chat: unread indicators, favourites, add by pubkey or from advert frames, remove, ROOM/REPEATER login
- Group chat: Public / Hash / Private groups; unread indicators, add/remove
- ChatView: message list + compose bar; send modes (Async/Sync/Retry); outgoing status indicators; SNR/RSSI from paired `LOG_RX_DATA`
- ChatManager: full lifecycle, message persistence (`ChatStore`, JSON per device/conversation), `setLogFramePairing(true)` for SNR/RSSI pairing
- Settings: theme (Default/System/Light/Dark), log history size, radio config (freq/BW/SF/CR/TX power/repeat), flood scope / regions (persisted default scope, session override, force-unscoped), backup/restore
- Log window: live PUSH frame stream

**Pending:**
- Device time sync on connect
- Reply-to message (quote + message hash from LOG_RX_DATA)
- Log window frame type filter
- QR code for contact sharing
- ROOM/REPEATER remote admin (command line, telemetry)

## Build Commands

```bash
# Build distributable zip for this machine's platform
# (output: target/bsmeshcorecompanion-desktop-<version>-<platform>.zip)
mvn package
# ... or for another one (win, linux, linux-aarch64, mac, mac-aarch64)
mvn package -Djavafx.platform=linux
# ... or for all of them at once (release; attached with the platform as classifier)
mvn package -Pdist

# Run during development (no zip needed)
mvn javafx:run

# Run single test
mvn test -Dtest=ClassName#methodName
```

No `javafx-web` (+ `javafx-media`, ~37 MB): optional in common-java-utils-jfx (0.6+), whose About
dialog (Alt+F1) is WebView-free. Its Credits tab lists the shipped third-party libraries; the ones
beyond common-java-utils-jfx are in `lib_credits` of `BSMeshcoreCompanionModule.xml` - keep it in
step with the dependencies.

The zip is per platform: JavaFX jars carry the natives of one platform (`javafx.platform`, the
OpenJFX classifier; `platform-*` profiles pick the build machine's). It contains `run.bat` (Windows,
no console window) and `run.sh` (Linux/macOS), plus `lib/` and `config/`. Both scripts change to the
app directory first: `config/`, `data/` and `log/` are resolved against the working directory.
`lib/` comes from the assembly's dependencySet, not a copied folder, so jars of earlier builds
can't leak in. Assembly layout: `src/assembly/component.xml` (app.jar, scripts, config - shared),
`app.xml` (default build), `dist/<platform>.xml` (`-Pdist`: all non-JavaFX dependencies plus the
JavaFX jars of that platform, which the dist profile copies to `target/dist/<platform>/lib` as the
classifier variants of the resolved `org.openjfx` dependencies).

All `cz.bliksoft.*` dependencies are published to Maven Central — no private repository needed.

## Architecture Overview

### Dependencies and Java Version

- **Java 21**, JavaFX 21.0.9
- `cz.bliksoft.meshcore:meshcore-companion:0.0.2-SNAPSHOT` — radio protocol library (`../Meshcore`)
- `cz.bliksoft.java:common-java-utils-jfx:0.1-SNAPSHOT` — application/GUI framework (`../BSToolbox-jfx`)
- `cz.bliksoft.java:common-java-utils:0.6-SNAPSHOT` — base utilities
- `cz.bliksoft.java:dependency-management:1.21.1-SNAPSHOT` — BOM for transitive deps
- `com.fazecast:jSerialComm:[2.0.0,3.0.0)` — USB serial transport
- `cz.bliksoft.java:common-java-utils-ble:0.3.0-SNAPSHOT` — BLE transport (`../BSToolbox-BLE`);
  pinned to a local snapshot install rather than the published 0.2.0 while fixes for a couple of
  Windows-only scan/discovery gaps (see BSToolbox-BLE's own history) are still unreleased

### BSToolbox-jfx Framework (`../BSToolbox-jfx`)

All application structure, lifecycle, and UI patterns come from this library:

- **`BSApp`** — static application hub; call `BSApp.init()` at startup; modules discovered via `ServiceLoader`
- **`IUIAction` / `ActionBinder`** — action interface wired to toolbar buttons; toolbar buttons are `ISavable`/`IClose`-aware
- **`FileLoader`** — XML-based declarative UI assembly (preferred over FXML)
- **`IStatusBean` / `IParentedStatusBean`** — observable bean lifecycle; all model objects implement this
- **`FxStateManager` / `FxStateBinder`** — persists window and control state between sessions
- **Properties hierarchy** — local config (`~/.BSMeshcoreCompanion/`) overrides global (`./.BSMeshcoreCompanion/`); provided by BSApp
- **Session/rights context** — `SessionManager` for user identity; application event bus for cross-component communication

### Meshcore Library (`../Meshcore`)

- **`SerialMeshcoreCompanion`** — USB/serial transport (use for COM port connections)
- **`MeshcoreCompanion`** — protocol logic; contacts, channels, frame listeners, send/receive loop
- **`FrameListenerRegistry`** — listener dispatch by frame class hierarchy; register listeners here for push frames
- **`FrameConstants`** — all frame type codes and enums in one place
- Outgoing commands: `cz.bliksoft.meshcore.frames.cmd.*` (CmdXxx classes)
- Incoming responses: `cz.bliksoft.meshcore.frames.resp.*` (RespXxx classes)
- Unsolicited push frames: `cz.bliksoft.meshcore.frames.push.*`
- OTA (over-the-air) frames: `cz.bliksoft.meshcore.otaframe.*`

### Reference Implementation (`../StorageManager/StorageManagerDesktopClient2`)

Follow this project's patterns for:
- `Main.java` entry point and `BSApp` initialization
- Module structure under `src/main/java/cz/bliksoft/storagemanager/desktop/`
- Meshcore event handling in `events/meshcore/`
- `pom.xml` plugin configuration (JavaFX Maven plugin, git-commit-id, build-helper, templating)
- Resource layout: `config/`, `icons/`, `log/` directories alongside the JAR

### Application Structure

```
src/main/java/cz/bliksoft/meshcorecompanion/
  ├── Main.java                    – Entry point, BSApp.init()
  ├── actions/                     – IUIAction implementations (connect, disconnect, settings, save, close)
  ├── connection/                  – Connection manager (USB/BLE selection, device persistence)
  ├── chat/                        – Chat models, message persistence, unread tracking
  ├── controls/                    – Custom UI controls (message list, contact list, editor)
  ├── events/                      – Application event bus handlers
  │   └── meshcore/                – Meshcore push frame → app event bridge
  ├── model/                       – Data models (contact, group, message, device config)
  ├── settings/                    – Settings pane, radio config get/set, backup/restore
  └── viewmodel/                   – View models for chat, contacts, groups
```

Data stored under `./data/` (chat history per device pubkey, frame logs, device backups).

## Product Specification

### UI Structure

**Toolbar** (right-aligned): connect / disconnect buttons (mutually exclusive), settings button, context-controlled save button (`ISavable`), close button (`IClose`)

**Main pane**: tabbed (contact chats, group chats, log window) with a push-in settings pane

**Status bar**: connection status, contacts count/capacity, groups count/capacity

### Chat Behaviour

- Left pane: contact or group list with add/remove, favourites, unread indicators
- Right pane: message list + editor; messages are short (firmware-limited) but unicode-aware
- Persistence: per-device, keyed by contact or group pubkey; stored in `./data/`
- Send modes: synchronous (with confirmation), direct (known route), flood routing; configurable retry count with optional final flood attempt
- Reply: uses original sender; includes original message hash when available from `LOG_RX_DATA`
- Message display: received timestamp, sender details, `LOG_RX_DATA` parsed data when available

### Contact / Group Types

- **ROOM / REPEATER contacts**: require login with password before chatting; support remote administration (command line, telemetry)
- **Groups**: Public (fixed pubkey), Hash (name starts with `#`, key derived), Private (custom name/key); all stored in-device
- Contacts: can be favourites, added by pubkey or from advert frames (discover), shared via QR code

### Radio / Context Integration

- All received PUSH frames → context events
- Connected device → application context (present when connected, removed on disconnect)
- Log window: live list of all PUSH frames with timestamps, type filter, parsed content; not persisted between restarts

### Connection Management

- Select transport (USB/BLE) or a previously saved device
- Enumerate COM ports or scan BLE devices
- Save devices with a user-given name in local config; option to forget

### Configuration

- Get/set radio device parameters grouped by firmware frame categories
- Application settings: dark/light/system theme, log history size
- Backup/restore via the library-provided method

### Design Goals

This application is also a driver for evolving `../BSToolbox-jfx` and `../Meshcore`. Generic improvements belong in those libraries; nothing app-specific should leak into BSToolbox-jfx, and nothing Meshcore-dependent should leak into BSToolbox-jfx.
