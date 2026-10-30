# Validation

[简体中文](TESTING.zh-CN.md)

## Automated checks

Run `./gradlew clean build` using Java 25. JUnit Jupiter checks history retention/order, configuration recovery, permissions, localization, JSON/backup recovery, world isolation, asynchronous clear/save races, final flush, filtering and recipient chat visibility. Snapshot/cache tests cover changed versus unchanged ticks, shrinking, dirty clearing, shutdown records and lazy decoding.

Version 0.2.0 adds format-2 round trips and rejection of older formats, server-capture timestamps, filtered rich components, client configuration validation, filtering before personal limits, opt-out selection, negotiation timeout/version mismatch, missing-channel fallback and forced-system preference retention. Protocol tests exercise the real Minecraft component codec and the custom history payload codec without player-chat signature validation.

Client compatibility contract tests check required Chat Patches method/field shapes and queued timestamp metadata. They do not run Chat Patches rendering or apply the mixin in a game. Server/player fixtures do not create a running world or network connection. These tests cannot establish real event ordering, actual UI behavior, disconnection cleanup, Fabric injection or live compatibility.

Client display regressions exercise real ChatAbilities restrictions and GameNarrator routing with a mocked speech backend, without game/audio constructors. They cover default narration skipping, local configuration reload, adapter restoration order, and no speech or retry after rejected/failed display.

## Manual acceptance — separate confirmation required

Use disposable installations and worlds. Record exact mod/client/server versions, settings, screenshots where useful and observed outcomes. Passing compilation or automated tests does not complete this checklist. Do not use real saves or accept the EULA as part of automated checks.

| Scenario | Expected result |
| --- | --- |
| Fabric 26.3 dedicated server, vanilla 26.3 client | Connects without client mods; eligible history uses system chat; next live chat works. No extra negotiation delay. |
| Both sides installed; client without Chat Patches, Chat Tools or Mod Menu | History, local commands, settings and status work independently. No standalone timestamp prefix. |
| Online mode with secure profiles on/off; offline mode | History works without changing policy or sending temporary player identities/sessions. |
| `auto` versus `system` | `auto` enhances compatible connections; `system` uses vanilla messages but retains accepted personal preferences. |
| Personal limit and all event switches, mixed history | Filters before taking latest N; chronological delivery; never enlarges server history or changes live chat. |
| Receive disabled; empty history; invalid server config | Successfully negotiated opt-out suppresses history and routine notices, including configuration errors. |
| Config-stage timeout, unsupported protocol or missing play channel | Join continues using server defaults with a warning. Accepted/current state is shown accurately; late replies cannot change it. |
| Disconnect/reconnect and server change | Negotiation and one-per-connection failure notices reset; global saved preferences remain. |
| Client screen, commands and optional Mod Menu | Saving/reloading works without server permissions; saved versus current values are clear; ordinary preferences apply next connection. |
| Chat Patches 8.0.0+26.3-fabric, enhanced path | Old player chat and all event types show server-capture time in configured timestamp, hover and context display; timezone follows Chat Patches. |
| Chat Patches timestamp/system-time toggles, names and duplicate merging | User settings remain authoritative; event messages respect system-time settings; merging/names/logging still work. |
| Chat Patches loading an existing chat log during replay | Queued records retain historical metadata when drained; subsequent live messages have their normal current time. |
| Message timestamp compatibility disabled, then client restarted | Our Chat Patches and Chat Tools time mixins are not applied and historical time metadata is not supplied; base features work. Existing Chat Patches behavior is unchanged. |
| Unsupported Chat Patches shape or runtime adapter failure | Base history continues; one warning and actionable status; no duplicate batch replay or stale metadata on live messages. |
| Client changes integration setting without restart | Status distinguishes saved versus startup state; restart requirement is visible. |
| Singleplayer, LAN host with cheats off, vanilla/enhanced LAN guests | Owner can administer; ordinary guests cannot; recipient preferences and histories remain isolated. |
| Switch between two worlds | Separate histories, shared installation configuration; no stale cached content. |
| Private/team chat, team-only deaths, blocked broadcasts | No private/restricted messages enter public history. Public event gamerules remain respected. |
| Filtered text, self-message exception, hidden/system-only chat, blocked senders | No filtered text leaks; visibility and client social restrictions are preserved. |
| Event toggle, shrinking, invalid config/reload, clearing and restart | Active configuration, eviction, recovery and notices match the README; pre-clear data does not return. |
| Stop with connected players | Final enabled leave events persist. |
| Wrapped text and large batches | Native/Chat Patches window limits apply; no pagination or cross-tick replay is promised. |
| Account/launcher prohibits player chat but allows system messages | Enhanced player history does not enter display, time integration or narration; system events remain available. |
| Narrator skipping at its default/on, Minecraft narrator enabled | Enhanced history entries are not submitted for speech; live chat and batch notices retain vanilla behavior. |
| Skipping off, Minecraft narrator off/on | No speech when off; when on, successfully inserted entries use vanilla system-chat narration once while retaining time integration. |
| Save/reload skipping with existing speech queued | Future enhanced entries use the new value; no queue clearing or rereading old entries; system fallback is unaffected. |

Before a performance decision, distinguish selection cost from decoding, packet encoding, real network queuing and client rendering. Background fixture benchmarks cover only the measured stages, not production TPS or end-to-end latency.

Configuration and integration-manager regressions cover the message timestamp compatibility setting, saves/reloads, and nested scopes, failure reporting and connection resets with multiple simulated adapters. Production time adapters include Chat Patches and Chat Tools; these unit tests do not establish in-game compatibility between multiple third-party mods.

## Chat Heads acceptance

The automated adapter tests cover exact UUID lookup, missing-player delegation, event isolation, metadata restoration, equal-text queue identity, connection cleanup, independent settings and no retries after insertion starts. Headless checks verify actual optional mixin transformation, not rendering or live compatibility.

| Scenario | Expected result |
| --- | --- |
| Chat Heads 1.3.2 alone; online sender | Enhanced player history uses the sender's head according to Chat Heads settings. |
| Chat Heads plus Chat Patches 8.0.0 | Heads and historical times both work; no duplicate head with name formatting or duplicate merging. |
| Before-name / before-line placement; wrapped text and refresh | Placement and associated sender survive wrapping and rebuilding the HUD. |
| Chat Patches loading old logs; two senders with identical text | Queued history retains each sender's head and timestamp; no stale head reaches live chat or events. |
| Sender offline or absent from current player information | Existing Chat Heads detection applies; full offline avatars are not required and no extra lookup/cache is added. |
| Time integration off, heads on / heads off, time on | The independent enabled capability remains; disabling heads and restarting removes our head queue hook. |
| Account restriction, blocked sender, narrator skipping | Rejected chat never reaches the head adapter; narration uses the original text and existing policy. |
| Unsupported interface, reset, 10000 entries | Basic history remains available, no duplicate insertion, connection metadata cleared; no per-message offline requests. |

## Chat Tools acceptance

Regressions cover every record type, timezones/DST, scope restoration, equal-text queue isolation, connection cleanup and upstream bytecode shapes. Headless checks with the official Chat Tools 2.4.5 JAR verify actual prefix/hover/copy time metadata and class loading alone, with Chat Patches/Chat Heads, disabled settings, invalid configuration, absent mods and server environment. They do not launch the game or invoke mod entrypoints. The following checks still require live verification.

| Scenario | Expected result |
| --- | --- |
| Chat Tools 2.4.5, player chat and all events | Prefix, hover date/offset and copy time use server-capture time; later live chat uses normal time. |
| Timestamp disabled, custom format, dates across DST | Respect Chat Tools settings and the local offset at the historical instant; no forced prefix. |
| With Chat Patches 8.0.0 and Chat Heads 1.3.2 | Historical times and existing head support work; extra prefixes can be disabled in upstream settings. |
| Join while Chat Patches loads logs; head integration disabled | Drained entries retain individual times, including identical text; disconnect clears pending metadata. |
| Time integration disabled and restarted, absent or unsupported mod | Corresponding time hooks stay inactive, basic history works, status explains why; head toggle remains independent. |
| Chat Tools duplicate merging, copy menu and history refresh | Follow upstream display/copy rules without repeated timestamp decoration; check merged time semantics and metadata. |
| Forced system fallback | Historical time is not promised; Chat Tools may show reception time. |
