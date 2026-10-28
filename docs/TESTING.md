# Validation

[简体中文](TESTING.zh-CN.md)

## Automated checks

Run `./gradlew clean build` using Java 25. Tests exercise history retention and order, event visibility, configuration validation/recovery, permissions policy, localization, JSON and backup recovery, world storage isolation, asynchronous clear/save races, final flush, filtering, native packet codecs, historical timestamps, and recipient-local player-info restoration after a replay failure.

`ReplayVisibilityTest` calls the production `visibleTo` entry point with a Mockito subclass of `ServerPlayer` and real vanilla permission sets. It covers ordinary players and every operator level across all three chat visibility settings, sender/recipient filtering, and the self-message exception. No world, connection, or attached mocking agent is required.

Protocol tests bootstrap vanilla registries and codecs and invoke the native unsigned validator without creating a world or connecting a client. They explicitly check that packet round trips retain historical time while `SignedMessageValidator.ACCEPT_UNSIGNED` replaces it with receive time. JUnit does not apply Fabric Mixins: the player-info packet test writes the accessor's target field reflectively so the actual vanilla serialization can be tested. Successful tests therefore do not prove loader injection, event ordering in a running server, or client acceptance.

Snapshot and cache regressions exercise end-of-tick persistence, unchanged ticks, immediate configuration shrinking, clearing dirty history, final shutdown records, immutable snapshot reuse, world-local cache eviction, lazy decoding, and recipient-specific filtering. WorldHistory tests use simulated server/player contexts and real files; they do not run a game server.

## Manual acceptance — separate confirmation required

Use disposable test installations and worlds. Do not use real saves. Record the server/mod/client versions and settings with the result. These checks have not been completed merely because the JAR builds.

| Scenario | Expected result |
| --- | --- |
| Fabric 26.3 server, vanilla 26.3 client | Client connects without extra mods; batch includes the current enabled join announcement; next live chat works. |
| Online mode, effective secure profiles enforced, `auto` | Player history is system chat; no historical signature/session validation error. |
| Online mode with `enforce-secure-profile=false`, `auto` | Unsigned history displays with receive time after client validation; live signed chat works after the batch. |
| Offline mode, `auto` | Unsigned history works; restart and sender reconnect do not cause invalid chat errors. |
| `system` in each authentication mode | All replay is system chat; no player-info changes needed. |
| Only Show Secure Chat enabled | Unsigned replay may be hidden; system fallback behavior and documentation agree with the client. |
| Sender offline, renamed, or reconnected; server restart | Old sender names and stored times remain (client display has the limitation above); no temporary sender appears in Tab; current session is restored. |
| Compatible released Chat Patches for 26.3 | Record the exact version. Version 8.0.0 shows receive time on the current unsigned replay path; system messages also lack original timestamps. Verify continued live chat. |
| Singleplayer, LAN host with cheats off, LAN vanilla guest | Owner can administer; ordinary guest cannot; all receive their eligible history. |
| Switch between two singleplayer worlds | Histories stay isolated; installation-level settings are shared. |
| Private/team chat, team-only deaths, blocked Fabric events | None leak into global history. Standard public events obey vanilla gamerules. |
| Text filtering and chat visibility | Self-message filtering follows vanilla; filtered content is not exposed in either replay mode. |
| Event toggle, capacity shrink, invalid config/reload | Visibility, eviction, active settings, red localized notice and console logging match the README. |
| Clear during ongoing chat, then restart and backup recovery | Pre-clear records never reappear after a successful clear; later messages remain. |
| Stop with connected players, then restart | Final leave events are included if enabled. |
| Long wrapped messages and a large history | Native chat limits are visible; no promised pagination or unlimited scrollback. |

Confirm this stage before launching a Minecraft client, server gameplay session, singleplayer world, or LAN test. No EULA acceptance is part of the automated build.
