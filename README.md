# Server Chat History

[简体中文](README.zh-CN.md)

A Fabric mod for **Minecraft Java 26.3** that replays a world's recent public chat when a player joins. Install it on the server; **vanilla clients do not need this mod or Fabric**. It also supports the logical server in singleplayer and LAN worlds.

Version 0.1.0 targets Java 25, Fabric Loader 0.20.1, and Fabric API 0.165.0+26.3. The project name is provisional; the mod ID is `server_chat_history`.

## Install

1. Set up a Fabric 26.3 server with Java 25 and Fabric Loader 0.20.1.
2. Put Fabric API 0.165.0+26.3 and `server-chat-history-0.1.0.jar` in its `mods` directory.
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
| `replayMode` | `auto` selects unsigned player chat when the server's effective secure profile policy allows it; otherwise system messages. `system` always uses system messages. Events always use system messages. |
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

## Timestamps, secure chat, and client limits

Storage and unsigned player-chat packets retain the event's timestamp. If vanilla has already reconstructed an unsigned incoming message with its receive time, that is the time saved. However, the Minecraft 26.3 client rebuilds the message body during unsigned validation on the current replay path, replacing its timestamp with the current receive time. Chat Patches 8.0.0 reads that replacement time, so **its built-in timestamp does not currently show the historical send time**. Preserving a packet timestamp does not guarantee its display on the client.

No original signatures or historical session keys are saved. Offline sender identities are temporarily supplied as unlisted player info, and current online chat sessions are restored after replay. When secure profiles are enforced, `auto` falls back to system messages, which have **no original chat timestamp field**. Setting `enforce-secure-profile=false` allows `auto` to use unsigned player chat but **does not fix the timestamp display limitation**; `online-mode` can remain enabled. The mod never changes either setting.

Unsigned historical player messages are not secure signed chat. A client's **Only Show Secure Chat** setting may hide them, and the server cannot detect that toggle. Use `system` if system-message delivery is preferable. Client chat visibility and other mods can also affect display.

Vanilla retains at most 100 chat messages and 100 rendered lines. Long messages wrap, so even the default 50 records may exceed the visible line budget. Batch notices and config errors also use chat lines without counting toward `historyLimit`.

**Compatibility status:** compilation and automated checks are separate from actual game validation. Vanilla multiplayer, singleplayer/LAN, and Chat Patches timestamp behavior require the manual acceptance checklist in [docs/TESTING.md](docs/TESTING.md). Do not interpret protocol tests as a completed client compatibility claim.

## Persistence

Each world uses `data/server_chat_history/history.json`, with one `.bak` recovery snapshot. Versioned JSON retains arrival sequence, event time, rich components, sender identity, raw message body, and filtering information. It contains public chat in readable form; manage world backups accordingly.

Ordinary records are combined into one immutable persistence snapshot at the end of each changed server tick. Unchanged snapshots are reused; a single background writer additionally coalesces pending ordinary writes. Configuration changes and clearing retain their explicit persistence boundaries, and normal shutdown waits for the final snapshot, including records received after the last tick. Temporary files and atomic replacement protect updates where the filesystem supports atomic moves. A crash or I/O failure can lose changes that have not been persisted; this is not a transactional chat archive.

Replay lazily caches decoded text components per world and shares them across recipients. Eviction, shrinking, clearing, and shutdown release obsolete cache entries. Visibility, text filtering, packet indices, and chat sessions remain recipient-specific. Replay still sends all retained, enabled records in the join tick; this optimization does not introduce pagination or spread replay across ticks.

An invalid primary file is quarantined as `.corrupt-<uuid>` and the backup is tried. If neither copy is usable, the mod starts empty and logs the error while preserving damaged files. If preservation itself fails, writes refuse to overwrite those files; repair permissions and restart, or explicitly clear. Storage failures leave recording active in memory and are retried on subsequent changes and shutdown.

## Build

```sh
./gradlew clean build
```

Use Java 25. The Gradle 9.6.1 wrapper verifies its distribution checksum. Build output is `build/libs/server-chat-history-0.1.0.jar`; the `-sources.jar` is for development. Tests are under `src/test`, and HTML results are under `build/reports/tests/test/`. GitHub Actions builds and uploads the JAR and test reports without publishing a release.

Internals separate capture, immutable records, bounded history, replay policy, vanilla transport, configuration, and persistence. See [docs/TESTING.md](docs/TESTING.md) for validation scope.

MIT license. Author: IceeAn.
