<h1 align="center">
  <img src="docs/assets/remember-that-message-superellipse.svg" alt="Remember that Message mod icon" width="128" height="128"><br />
  Remember that Message
</h1>

<p align="center">A server-side Minecraft mod that records chat messages, even when you're offline.</p>

<p align="center">
  <strong>0.3.0</strong> · Fabric · Server-side installation · <a href="LICENSE">MIT</a>
</p>

<p align="center">
  <strong>English</strong> · <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <a href="#install">Install</a> ·
  <a href="#configuration">Configure</a> ·
  <a href="#administration">Commands</a> ·
  <a href="#optional-client-installation">Client extras</a> ·
  <a href="docs/COMPATIBILITY.md">Version support</a> ·
  <a href="#development-and-build">Build</a>
</p>

---

For **Minecraft Java 1.20–1.20.6, 1.21–1.21.11 and 26.3**. Install on a Fabric server to replay a world's recent public chat when players join. **Vanilla clients need neither this mod nor Fabric.**

| Feature | What you get |
| --- | --- |
| History on join | Recent chat and enabled join, leave, death and advancement announcements, replayed oldest first. |
| Per-world storage | **50 records** by default, with configurable capacity and history that persists across restarts. |
| Optional client extras | The same JAR adds personal preferences, a settings screen and supported timestamp/head integrations. |
| Flexible hosting | Dedicated servers, singleplayer worlds and the logical server of a LAN host. |

> First-time visitors receive history too.

## Install

1. Put the Fabric API and mod JAR matching your Minecraft version in the server's `mods` directory.
2. Restart the server.

The configuration file is located at `config/remember_that_message.json`; history files are stored in the world save.

See the [version support matrix](docs/COMPATIBILITY.md) for dependency versions and mod integration scope.

For singleplayer or LAN hosting, the host can install these mods in their Fabric game instance.

## What is recorded

The default capacity is **50 records per world**.

Public player chat and public join, leave, death and advancement announcements are recorded. Private messages, team chat, restricted-audience deaths, command output, `/say`, `/me`, `/tellraw` and external chat bridges are **not** recorded.

## Configuration

The configuration file is `config/remember_that_message.json`, located in the game or server installation.

Singleplayer saves in the same installation share this configuration, but their message histories remain separate.

```json
{
  "historyLimit": 50,
  "events": {
    "join": true,
    "leave": true,
    "death": true,
    "advancement": true
  },
  "replayMode": "auto",
  "showEmptyHistory": true,
  "showConfigErrors": true,
  "shortCommandAliases": true
}
```

| Key | Meaning |
| --- | --- |
| `historyLimit` | Maximum number of history records to retain and send; a positive 64-bit integer. |
| `events.join`, `events.leave`, `events.death`, `events.advancement` | Whether to record and display join, leave, death and advancement messages. |
| `replayMode` | `auto` uses the enhanced protocol for clients with the mod installed and vanilla system messages otherwise; `system` forces system messages. |
| `showEmptyHistory` | Whether to show a notice when no eligible records exist. |
| `showConfigErrors` | Whether to report configuration errors during history replay on join. |
| `shortCommandAliases` | Whether to enable the server's `/rtm` alias. Changes take effect after a **server restart**. |

Shrinking capacity immediately evicts older records. Growing it cannot recover evicted data.

## Administration

Console, OP level 2 or higher, and the singleplayer/LAN world owner can administer history. The owner does not need cheats enabled. Ordinary LAN guests cannot. Permission-backed command blocks can also execute the commands.

The examples below use the `/rtm` alias. If aliases are disabled or the name conflicts with another command, use the full `/rememberthatmessage` command instead.

```text
/rtm config
/rtm config get historyLimit
/rtm config set historyLimit 100
/rtm config set events.death false
/rtm config set replayMode system
/rtm reload
/rtm status
/rtm clear
```

Command changes are applied immediately, except for `shortCommandAliases`, which requires a server restart. `reload` loads manual file edits. `status` reports retained/visible counts, effective replay mode, configuration and storage health, and pending writes. A recovery warning remains visible for the session after a damaged file is recovered; consult the log.

`clear` empties the current world's in-memory history and removes its recovery copies, including quarantined corrupt history. Messages arriving after the clear are retained normally.

## Optional client installation

Install the matching JAR and Fabric API in a Fabric client of the same Minecraft version.

### Client settings and commands

The examples below use the `/rtmclient` alias. If aliases are disabled or the name conflicts with another command, use the full `/rememberthatmessageclient` command instead.

Use `/rtmclient` to open the settings screen (also available through Mod Menu), or use these commands:

```text
/rtmclient status
/rtmclient config get historyLimit
/rtmclient config set historyLimit 100
/rtmclient config set events.join false
/rtmclient config set receiveHistory false
/rtmclient config set messageTimeIntegration false
/rtmclient config set chatHeadsIntegration false
/rtmclient config set skipHistoryNarration false
/rtmclient reload
```

| Setting | Effect |
| --- | --- |
| `receiveHistory` | Whether to receive history sent by the server mod. |
| `historyLimit` | Limits the number of history messages received; `0` follows the server. |
| `events.join`, `events.leave`, `events.death`, `events.advancement` | Whether to display join, leave, death and advancement messages in history. |
| `messageTimeIntegration` | Whether supported mods (currently Chat Patches and Chat Tools) display the server-recorded message time. Changes require a **client restart**. |
| `chatHeadsIntegration` | Whether Chat Heads displays online players' avatars in historical messages. Changes require a **client restart**. |
| `skipHistoryNarration` | Whether the narrator skips historical messages. |
| `shortCommandAliases` | Whether to enable the client's `/rtmclient` alias. Changes require a **client restart**. |

`messageTimeIntegration`, `chatHeadsIntegration` and `skipHistoryNarration` require server `replayMode=auto` and successful negotiation with the server to take effect.

Changes to `receiveHistory`, `historyLimit`, `events` and `skipHistoryNarration` require no client restart and take effect when you next connect to a server and receive history. With the optional client installed, history preferences are negotiated on connection. Failed negotiation falls back to the server defaults and displays a warning.

Changes to `messageTimeIntegration`, `chatHeadsIntegration` and `shortCommandAliases` require a client restart.

## Historical timestamps and player heads

With this mod and Chat Patches or Chat Tools installed on the client, `messageTimeIntegration` (enabled by default) lets those mods display the message time recorded by the server, rather than the time the history arrives when you join.

With this mod and Chat Heads installed on the client, `chatHeadsIntegration` (enabled by default) lets historical messages display avatars for players already online when you join. Offline-player avatars are not currently supported.

See the [support matrix](docs/COMPATIBILITY.md) for specific Chat Patches, Chat Tools and Chat Heads versions and the scope of validation.

If these integrations cause problems, try setting `messageTimeIntegration=false` or `chatHeadsIntegration=false` to disable the corresponding feature, then restart the client.

## Persistence

History is stored in each world's `data/remember_that_message/history.json`, with a `.bak` recovery snapshot.

Changes to ordinary records are submitted for background saving at the end of the server tick in which they occur, on a best-effort basis. An invalid primary file is quarantined as `.corrupt-<uuid>` before recovery from the backup is attempted.

When migrating between Minecraft versions, the mod attempts to convert the records and retains at most one pre-migration backup as `history.json.previous-version`.

## Development and build

```sh
./gradlew clean build
```

Run Gradle with Java 25 and also install Java 17 and Java 21 toolchains. The Gradle 9.6.1 wrapper verifies its distribution checksum. This command builds all 20 targets. `./gradlew -PgameTargets=1.20.4,1.21.11 build` configures and builds only those targets plus common. Tasks such as `:mc263:build` remain available, but all targets are configured unless `gameTargets` is supplied.

Artifacts use `versions/<version>/build/libs/remember-that-message-0.3.0+mc<version>.jar`.

The `-sources.jar` files are for development. The root project aggregates builds; it does not represent the newest Minecraft version.

### Project structure

- `common/`: independently compiled Java 17 business code, configuration, persistence, protocol contracts and language resources. It has no Minecraft or Fabric dependency. Run `./gradlew :common:test --tests '*PersistenceTest'` for a focused persistence check.
- `platform-shared/src/`: explicit shared game adapters and integration tests, compiled against each target's dependencies. These sources may depend on Minecraft/Fabric; neither version inherits the other's files.
- `platform-adapters/`: thin implementations and tests grouped by actual API differences; see the [adapter guide](platform-adapters/README.md).
- `versions/<version>/target.properties`: dependencies, Java version and adapter selection. Only unshared implementations remain in version-specific `src/`. Root `build.gradle.kts` defines common build conventions.

Each installable JAR contains the common classes/resources once and only its own platform implementation. No separate common mod is required. Common code owns the storage envelope, JSON conversion and stable wire protocol. Modern targets use typed payloads; 1.20–1.20.4 wrap the same bytes with its older channel API.

HTML test reports are under each module's `build/reports/tests/test/`. GitHub Actions installs all three toolchains and uploads installable JARs through a target matrix and all test reports without publishing a release.

Internals separate capture, immutable records, bounded history, replay policy, vanilla/enhanced transport, optional client integration, configuration, and persistence. See [docs/TESTING.md](docs/TESTING.md) for validation scope.

## License

[MIT](LICENSE) · Created by IceeAn.
