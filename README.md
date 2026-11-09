# Server Chat History

[简体中文](README.zh-CN.md)

A Fabric mod for **Minecraft Java 1.20–1.20.6, 1.21–1.21.11 and 26.3** that replays a world's recent public chat when a player joins. Install it on the server; **vanilla clients do not need this mod or Fabric**. The same JAR optionally adds client preferences, a settings screen, and historical timestamps through Chat Patches or Chat Tools. It also supports the logical server in singleplayer and LAN worlds.

Version 0.2.0 provides separate JARs for each Minecraft version, using Fabric Loader 0.20.1. The project name is provisional; the mod ID is `server_chat_history`.

## Install

1. Set up a Fabric server with the matching Java version and Fabric Loader 0.20.1.
2. Put the matching Fabric API and mod JAR from the table below in its `mods` directory.
3. Start the server. The mod creates `config/server_chat_history.json` and a history file inside the world.

| Minecraft | Java runtime | Fabric API | Mod JAR |
| --- | --- | --- | --- |
| 26.3 | 25 | 0.165.0+26.3 | `server-chat-history-0.2.0+mc26.3.jar` |
| 1.21.1 | 21 | 0.116.17+1.21.1 | `server-chat-history-0.2.0+mc1.21.1.jar` |
| 1.20.1 | 17 | 0.92.12+1.20.1 | `server-chat-history-0.2.0+mc1.20.1.jar` |

The table shows three examples; see the [complete 20-target matrix](docs/COMPATIBILITY.md) for dependencies and integration scope.

Install only the matching JAR. All targets support automatic history migration and a stable Play protocol. Cross-version connections require a proxy/protocol translator forwarding `server_chat_history:bridge_v2` and its registration; this mod does not translate Minecraft itself. Live Velocity/Via compatibility remains unverified.

For singleplayer or LAN hosting, install those mods in the host's Fabric game instance. Guests may use vanilla clients of the same Minecraft version. This mod does not change authentication, secure profile enforcement, or the server EULA.

## What is recorded

The default capacity is **50 records per world**, shared across dimensions. Public player chat and public join, leave, death, and advancement announcements are recorded. A multi-line message counts as one record. Each successful join receives the retained, enabled records in arrival order, including that join's announcement if enabled and broadcast. Candidates freeze at the end of the first join tick. Replay runs once after Play negotiation or an approximately two-second timeout; live messages during the wait are not replayed again and world entry is not blocked.

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

Install the matching JAR and Fabric API in a Fabric client of the same Minecraft version. Chat Patches, Chat Tools, Chat Heads and Mod Menu are **optional**, not bundled. Without a supported timestamp mod, all client preferences work; this mod adds no timestamp prefix or separate history panel.

Use `/chathistoryclient` for the settings screen (also available through Mod Menu), or:

```text
/chathistoryclient status
/chathistoryclient config get historyLimit
/chathistoryclient config set historyLimit 100
/chathistoryclient config set events.join false
/chathistoryclient config set receiveHistory false
/chathistoryclient config set messageTimeIntegration false
/chathistoryclient config set chatHeadsIntegration false
/chathistoryclient config set skipHistoryNarration false
/chathistoryclient reload
```

These are local commands requiring no server permission. In 1.21.1/1.20.1, chat `RUN_COMMAND` clicks targeting `/chathistoryclient` are blocked; type the command or use a suggestion that you explicitly submit. `config/server_chat_history-client.json` holds global preferences shared by all servers:

| Setting | Default and effect |
| --- | --- |
| `receiveHistory` | `true`; disabling suppresses history and its start/end, empty and configuration-error notices after successful negotiation. |
| `historyLimit` | `0` follows the server; a positive integer limits eligible records. |
| `events.join`, `events.leave`, `events.death`, `events.advancement` | All `true`; filter history only, not live chat or server storage. |
| `messageTimeIntegration` | `true`; enables supported historical-time integrations (Chat Patches and Chat Tools). Changes require a **client restart**. |
| `chatHeadsIntegration` | `true`; lets Chat Heads process player history using available player information. Independent of time integration; requires a **client restart**. |
| `skipHistoryNarration` | `true`; skips enhanced history entries in narration without enabling or disabling Minecraft's narrator. |

Receiving preference changes apply on the **next connection**. They do not remove, reinsert or rewrite existing chat or Chat Patches logs. The server filters by its own policy, recipient visibility and personal event preferences **before** selecting the latest N eligible records, then sends them oldest first. Preferences can only narrow retained server history.

A supporting client negotiates during Play, with an approximately two-second timeout; the old same-version configuration path remains available. Incompatible, incomplete or timed-out negotiation falls back to server-default history and displays a warning; personal preferences, including opt-out, may not apply in that case. Vanilla clients need no negotiation. Client `status` distinguishes saved preferences from those accepted for this connection.

“Narrator skips history” applies to subsequently received enhanced entries after saving or reloading. When disabled, successfully inserted entries use vanilla system-chat narration, which still respects Minecraft's narrator setting. It does not enable the narrator, reread existing history or clear queued speech. Large histories can queue up and delay live-message narration, so skipping is enabled by default. Live chat, batch notices and system-message fallback retain vanilla behavior; fallback messages cannot reliably be identified as history. Find the setting on the Narrator tab of `/chathistoryclient`.

Enhanced replay also honors client-side account/launcher restrictions on receiving player chat. When player chat is prohibited, player history is neither displayed nor narrated; events use system-message reception rules. Sender blocking remains in effect, as do friends-only restrictions where supported by Minecraft. The 1.20/1.21 ports follow their native `ChatStatus` rules, including local-world exceptions.

## Historical time compatibility

All event times are **server capture times**, not client-declared send times. The enhanced protocol carries them independently of signed player-chat validation. It neither changes secure-profile policy nor requires `enforce-secure-profile=false`. Historical messages are not authenticated player signatures, and no temporary player identities or chat sessions are sent.

With both sides installed, server `auto`, and compatible Chat Patches integration enabled, the adapter supplies the stored time to Chat Patches for player chat and events. Chat Patches retains control of formatting, timezone, hover/context display, names, duplicate merging and logging. Its own time/system-message settings still apply. Server `system`, a vanilla client, or absent/disabled/failed integration cannot provide historical time through this adapter; Chat Patches may show reception time instead.

Exact Chat Patches targets are listed in the [support matrix](docs/COMPATIBILITY.md). **Chat Patches integration is not provided for 1.20.2–1.20.6 or 1.21.2–1.21.10; time/head combinations with it are also outside the supported scope**. Other versions are attempted only if the required interface/bytecode checks pass. Passing those checks is **not a completed live compatibility test**; client status marks the integration as unverified in game. An incompatibility or runtime failure disables the adapter, reports the reason and warns once per connection while basic history remains available. Already displayed batches are not resent.

After setting `messageTimeIntegration=false` and restarting, this mod applies **no Chat Patches or Chat Tools time Mixins and supplies no historical time metadata to either mod**. Those mods still process ordinary chat according to their own settings. The switch does not disable this mod's independent networking or preferences.

### Chat Tools

The additional target is **Chat Tools 2.4.5 for the matching Fabric targets in the support matrix**, controlled by the same `messageTimeIntegration` setting. On the enhanced path, player chat and events use server-capture time for its timestamp prefix, hover date/time and message time metadata used by copying. Chat Tools retains its timestamp toggle, format and local timezone behavior; this mod does not add a second prefix itself or change its other HUD features. System fallback cannot supply historical time through this adapter.

An optional Chat Patches queue hook carries this time through delayed insertion, independently of the Chat Heads toggle. If both timestamp mods enable their prefixes, both may appear; choose the desired display in their own settings. `/chathistoryclient status` reports each adapter separately. Missing or incompatible Chat Tools leaves basic history available. After disabling time compatibility and restarting, its time and time-queue hooks are not applied; the independent head queue hook may remain enabled.

Automated tests and headless checks with the official JAR verify time scopes, actual timestamp transformations, and class loading with Chat Patches/Chat Heads. In-game rendering, copy menus, duplicate merging and live compatibility still require the acceptance checks.

## Chat Heads compatibility

The Compatibility tab contains separate time and Chat Heads toggles. With Chat Heads **the releases listed in the support matrix**, enhanced player history enters its existing head-detection logic using the sender UUID and the current connection's player information. Its detection, placement and rendering settings remain authoritative. Events do not receive a player head through this adapter. A missing player profile falls back to Chat Heads' own detection; this mod adds no offline profile cache, skin requests or fake player-list entries, and does not promise offline avatars or historical skins.

When Chat Patches **from the supported matrix entries** is also installed, an optional queue hook retains head metadata while its log loads, then restores it during insertion without decorating twice. This head-specific Chat Patches hook is independent of `messageTimeIntegration`; disabling time integration alone does not disable it. To disable this mod's head integration and queue hook, set `chatHeadsIntegration=false` and restart. Chat Heads' own behavior on ordinary messages remains intact. Missing/unsupported integrations leave basic history available; `status` lists their state and a runtime error disables head integration for the connection.

The implementation uses available player information only. Supporting full offline avatars remains future work. Automated tests and headless class transformation checks do not replace in-game checks of rendering, merged messages or loading existing logs.

Vanilla retains at most 100 chat messages and 100 rendered lines. Wrapping and batch notices consume that budget; a larger server limit does not enlarge it. Replay remains within one tick after negotiation or timeout, without pagination or interleaved tick batches.

**Validation status:** unit tests do not prove actual Fabric injection; headless transformation checks do not prove game compatibility. See [docs/TESTING.md](docs/TESTING.md) for the separate vanilla, optional-client, dedicated-server, singleplayer/LAN, Chat Patches and Chat Tools acceptance checks.

## Persistence

Each world stores `data/server_chat_history/history.json` plus a `.bak` recovery snapshot. Format 3 records `minecraftVersion` and `componentEncoding` once in the header. Records retain native JSON, capture time, order, sender identity and the separate filtered alternative. Matching encodings do not convert; supported different encodings migrate automatically. Known unmarked format-2 data can upgrade; ambiguous structures and future formats are preserved with writes blocked and an explicit error.

Ordinary records are combined into one immutable persistence snapshot at the end of each changed server tick. Unchanged snapshots are reused; a single background writer additionally coalesces pending ordinary writes. Configuration changes and clearing retain their explicit persistence boundaries, and normal shutdown waits for the final snapshot, including records received after the last tick. Temporary files and atomic replacement protect updates where the filesystem supports atomic moves. A crash or I/O failure can lose changes that have not been persisted; this is not a transactional chat archive.

Replay lazily caches decoded text components per world and shares them across recipients. Eviction, shrinking, clearing, and shutdown release obsolete cache entries. Visibility and text filtering remain recipient-specific. Replay still sends all retained, enabled records in one tick after negotiation or timeout; this optimization does not introduce pagination or spread replay across ticks.

An invalid primary file is quarantined as `.corrupt-<uuid>` and the backup is tried. If neither copy is usable, the mod starts empty and logs the error while preserving damaged files. If preservation itself fails, writes refuse to overwrite those files; repair permissions and restart, or explicitly clear. Storage failures leave recording active in memory and are retried on subsequent changes and shutdown.

## Build

```sh
./gradlew clean build
```

Run Gradle with Java 25 and also install Java 17 and Java 21 toolchains. The Gradle 9.6.1 wrapper verifies its distribution checksum. This command builds all 20 targets. `./gradlew -PgameTargets=1.20.4,1.21.11 build` configures and builds only those targets plus common. Tasks such as `:mc263:build` remain available, but all targets are configured unless `gameTargets` is supplied.

Artifacts use `versions/<version>/build/libs/server-chat-history-0.2.0+mc<version>.jar`.

The `-sources.jar` files are for development. The root project aggregates builds; it does not represent the newest Minecraft version.

- `common/`: independently compiled Java 17 business code, configuration, persistence, protocol contracts and language resources. It has no Minecraft or Fabric dependency. Run `./gradlew :common:test --tests '*PersistenceTest'` for a focused persistence check.
- `platform-shared/src/`: explicit shared game adapters and integration tests, compiled against each target's dependencies. These sources may depend on Minecraft/Fabric; neither version inherits the other's files.
- `platform-adapters/`: thin implementations and tests grouped by actual API differences; see the [adapter guide](platform-adapters/README.md).
- `versions/<version>/target.properties`: dependencies, Java version and adapter selection. Only unshared implementations remain in version-specific `src/`. Root `build.gradle.kts` defines common build conventions.

Each installable JAR contains the common classes/resources once and only its own platform implementation. No separate common mod is required. Common code owns the storage envelope, JSON conversion and stable wire protocol. Modern targets use typed payloads; 1.20–1.20.4 wrap the same bytes with its older channel API.

HTML test reports are under each module's `build/reports/tests/test/`. GitHub Actions installs all three toolchains and uploads installable JARs through a target matrix and all test reports without publishing a release.

Internals separate capture, immutable records, bounded history, replay policy, vanilla/enhanced transport, optional client integration, configuration, and persistence. See [docs/TESTING.md](docs/TESTING.md) for validation scope.

MIT license. Author: IceeAn.

## Cross-version history

Migration validates the entire result before replacing the file. At most one old-version copy is kept as `history.json.previous-version`; the ordinary `.bak` contains the new format after success. Unmappable content may degrade to readable text or an explicit placeholder. Item/entity hover data becomes text; unsupported interactions/styles are reported, not silently claimed as preserved. Logs and `/chathistory status` report source, destination, record count and losses. Filtered content is converted independently and never replaced by its unfiltered counterpart. To roll back, stop the server and restore the previous-version copy with the matching build. Explicit `clear` also removes this migration backup.

Portable JSON is generated lazily only for cross-version recipients, cached separately for original and filtered content, shared across recipients and released on eviction/clear/shutdown. It is not stored twice on disk. Wire text is limited to 256 KiB UTF-8 and conversion nesting to 64; oversize content is rejected, not silently truncated. Clients show one simplification warning. The old configuration-phase protocol remains a same-version fallback on 1.20.5 and later targets; cross-version replay requires the new Play protocol on both ends. Build only 1.20.1 with `./gradlew -PgameTargets=1.20.1 build`.

New Play replay replaces an individual conversion or encoding failure with a short `[Chat History] This record could not be displayed.` placeholder, retaining its order and time while continuing subsequent records. Stored history is unchanged. Client-side text parsing failures also use placeholders. Original reception restrictions still apply, but timestamp/head integrations receive a system presentation without player attribution. On the closing boundary, updated clients check record counts and unavailable entries, retain displayed content and warn once if replay is incomplete; they do not automatically retry. Older clients can display the placeholder's English fallback. Actual network-send failures still abort the batch.
