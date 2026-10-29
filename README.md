# Server Chat History

[简体中文](README.zh-CN.md)

A Fabric mod for **Minecraft Java 26.3** that replays a world's recent public chat when a player joins. Install it on the server; **vanilla clients do not need this mod or Fabric**. The same JAR optionally adds client preferences, a settings screen, and Chat Patches historical timestamps. It also supports the logical server in singleplayer and LAN worlds.

Version 0.2.0 targets Java 25, Fabric Loader 0.20.1, and Fabric API 0.165.0+26.3. The project name is provisional; the mod ID is `server_chat_history`.

## Install

1. Set up a Fabric 26.3 server with Java 25 and Fabric Loader 0.20.1.
2. Put Fabric API 0.165.0+26.3 and `server-chat-history-0.2.0.jar` in its `mods` directory.
3. Start the server. The mod creates `config/server_chat_history.json` and a history file inside the world.

For singleplayer or LAN hosting, install those mods in the host's Fabric game instance. Guests may use vanilla 26.3. This mod does not change authentication, secure profile enforcement, or the server EULA.

## What is recorded

The default capacity is **50 records per world**, shared across dimensions. Public player chat and public join, leave, death, and advancement announcements are recorded. A multi-line message counts as one record. Each successful join receives the retained, enabled records in arrival order, including that join's announcement if enabled and broadcast. Replay runs at the end of the join tick, after vanilla finishes setting up the connection.

Private messages, team chat, restricted-audience deaths, command output, `/say`, `/me`, `/tellraw`, and external chat bridges are outside the initial scope. Capture observes Fabric's allowed public broadcast events and standard vanilla message types. Mods that replace those paths or forge vanilla event components may require explicit integration.

Rich text, names, colors, translations, and hover information are preserved. Only the batch has start/end notices; individual records receive no extra prefix or timestamp. Filtering and the recipient's chat visibility settings are respected. Replay and mod notices are never recorded again.

This version sends recent history to **every joining player**, including first-time visitors and quick reconnects. It does not track which messages an individual missed, and has no player query or pagination command.

## Configuration

The config belongs to the game/server installation, so singleplayer saves in the same installation share it. Histories themselves remain world-specific.

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
  "showConfigErrors": true
}
```

| Key | Meaning |
| --- | --- |
| `historyLimit` | Positive 64-bit integer. No additional fixed cap or upfront allocation; actual memory, disk, and client limits still apply. |
| `events.join`, `events.leave`, `events.death`, `events.advancement` | Capture and display that event category. Disabled saved events remain in the capacity budget and reappear if reenabled before eviction. |
| `replayMode` | `auto` uses the enhanced history protocol for compatible clients and vanilla system messages otherwise. `system` always uses system messages; successfully negotiated client preferences still apply. |
| `showEmptyHistory` | Show a notice when no eligible records exist. |
| `showConfigErrors` | Append a red config-error notice on join, independently of the empty-history notice. |

Shrinking capacity immediately evicts older records. Growing it cannot recover evicted data. Missing fields use defaults; unknown fields and invalid values are rejected. On invalid startup config, defaults are used and the file is preserved. A failed reload retains the last valid effective config. A successful load or command save clears the config-error state.

## Administration

Console, OP level 2 or higher, and the singleplayer/LAN world owner can administer history. The owner does not need cheats enabled. Ordinary LAN guests cannot. Permission-backed command blocks can also execute the commands.

```text
/chathistory config
/chathistory config get historyLimit
/chathistory config set historyLimit 100
/chathistory config set events.death false
/chathistory config set replayMode system
/chathistory reload
/chathistory status
/chathistory clear
```

Command changes are validated, saved, and applied immediately. `reload` loads manual file edits. `status` reports retained/visible counts, effective replay mode, configuration and storage health, and pending writes. A recovery warning remains visible for the session after a damaged file is recovered; consult the log.

`clear` empties the current world's memory and removes its recovery copies, including quarantined corrupt history. It cancels queued old snapshots, waits behind any in-flight write, and reports success only after both the main file and backup are reset. A failed clear is reported explicitly and retried on the next save or shutdown. Records arriving after the clear are retained normally. External backups are unaffected.

Player notices use Simplified Chinese only for `zh_cn`, otherwise English. Console output and logs use English; no client translation resource pack is required.

## Optional client installation

Install the same JAR and Fabric API in a Fabric 26.3 client. Chat Patches and Mod Menu are **optional**, not bundled. Without Chat Patches, all client preferences work; this mod adds no timestamp prefix or separate history panel.

Use `/chathistoryclient` for the settings screen (also available through Mod Menu), or:

```text
/chathistoryclient status
/chathistoryclient config get historyLimit
/chathistoryclient config set historyLimit 100
/chathistoryclient config set events.join false
/chathistoryclient config set receiveHistory false
/chathistoryclient config set chatPatchesIntegration false
/chathistoryclient reload
```

These are local commands requiring no server permission. `config/server_chat_history-client.json` holds global preferences shared by all servers:

| Setting | Default and effect |
| --- | --- |
| `receiveHistory` | `true`; disabling suppresses history and its start/end, empty and configuration-error notices after successful negotiation. |
| `historyLimit` | `0` follows the server; a positive integer limits eligible records. |
| `events.join`, `events.leave`, `events.death`, `events.advancement` | All `true`; filter history only, not live chat or server storage. |
| `chatPatchesIntegration` | `true`; changes require a **client restart**. |

Ordinary preference changes apply on the **next connection**. They do not remove, reinsert or rewrite existing chat or Chat Patches logs. The server filters by its own policy, recipient visibility and personal event preferences **before** selecting the latest N eligible records, then sends them oldest first. Preferences can only narrow retained server history.

A supporting client negotiates during configuration, with a two-second timeout. Incompatible, incomplete or timed-out negotiation falls back to server-default history and displays a warning; personal preferences, including opt-out, may not apply in that case. Vanilla clients need no negotiation. Client `status` distinguishes saved preferences from those accepted for this connection.

## Historical time and Chat Patches

All event times are **server capture times**, not client-declared send times. The enhanced protocol carries them independently of signed player-chat validation. It neither changes secure-profile policy nor requires `enforce-secure-profile=false`. Historical messages are not authenticated player signatures, and no temporary player identities or chat sessions are sent.

With both sides installed, server `auto`, and compatible Chat Patches integration enabled, the adapter supplies the stored time to Chat Patches for player chat and events. Chat Patches retains control of formatting, timezone, hover/context display, names, duplicate merging and logging. Its own time/system-message settings still apply. Server `system`, a vanilla client, or absent/disabled/failed integration cannot provide historical time through this adapter; Chat Patches may show reception time instead.

The target is **Chat Patches 8.0.0+26.3-fabric**. Other versions are attempted only if the required interface/bytecode checks pass. Passing those checks is **not a completed live compatibility test**; client status marks the integration as unverified in game. An incompatibility or runtime failure disables the adapter, reports the reason and warns once per connection while basic history remains available. Already displayed batches are not resent.

After setting `chatPatchesIntegration=false` and restarting, this mod applies **no Chat Patches Mixin and accesses no Chat Patches internal metadata**. Chat Patches itself still processes ordinary chat according to its own settings. The switch does not disable this mod's independent networking or preferences.

Vanilla retains at most 100 chat messages and 100 rendered lines. Wrapping and batch notices consume that budget; a larger server limit does not enlarge it. Replay remains within the join tick, without pagination or interleaved tick batches.

**Validation status:** automated checks do not prove actual Fabric injection or game compatibility. See [docs/TESTING.md](docs/TESTING.md) for the separate vanilla, optional-client, dedicated-server, singleplayer/LAN and Chat Patches acceptance checks.

## Persistence

Each world uses `data/server_chat_history/history.json`, with one `.bak` recovery snapshot. Format version 2 retains arrival sequence, server capture time, rich components, sender identity, and any required filtered component. Redundant raw player-chat packet data is no longer stored. Older history formats are not supported. It contains public chat in readable form; manage world backups accordingly.

Ordinary records are combined into one immutable persistence snapshot at the end of each changed server tick. Unchanged snapshots are reused; a single background writer additionally coalesces pending ordinary writes. Configuration changes and clearing retain their explicit persistence boundaries, and normal shutdown waits for the final snapshot, including records received after the last tick. Temporary files and atomic replacement protect updates where the filesystem supports atomic moves. A crash or I/O failure can lose changes that have not been persisted; this is not a transactional chat archive.

Replay lazily caches decoded text components per world and shares them across recipients. Eviction, shrinking, clearing, and shutdown release obsolete cache entries. Visibility and text filtering remain recipient-specific. Replay still sends all retained, enabled records in the join tick; this optimization does not introduce pagination or spread replay across ticks.

An invalid primary file is quarantined as `.corrupt-<uuid>` and the backup is tried. If neither copy is usable, the mod starts empty and logs the error while preserving damaged files. If preservation itself fails, writes refuse to overwrite those files; repair permissions and restart, or explicitly clear. Storage failures leave recording active in memory and are retried on subsequent changes and shutdown.

## Build

```sh
./gradlew clean build
```

Use Java 25. The Gradle 9.6.1 wrapper verifies its distribution checksum. Build output is `build/libs/server-chat-history-0.2.0.jar`; the `-sources.jar` is for development. Tests are under `src/test`, and HTML results are under `build/reports/tests/test/`. GitHub Actions builds and uploads the JAR and test reports without publishing a release.

Internals separate capture, immutable records, bounded history, replay policy, vanilla/enhanced transport, optional client integration, configuration, and persistence. See [docs/TESTING.md](docs/TESTING.md) for validation scope.

MIT license. Author: IceeAn.
