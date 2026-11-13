# Platform source composition

This directory groups sources by actual game or third-party API differences, not by a version inheritance tree. Each `versions/<version>/target.properties` explicitly selects its `adapters`; the root build compiles those directories with `platform-shared/src`. A release includes only its selected implementations, with no path overlays or per-message version checks.

- `network-*`: Fabric registration, negotiation and native packet envelopes.
- `text-*`, `hover-event-*`: native component codecs and matching test fixtures.
- `language-*`, `profile-*`, `runtime-version-*`: player language, profile access and actual version identity.
- `client-platform-*`, `client-screen-*`, `chat-visibility-*`, `narrator-*`: display, settings, reception restrictions and native narration.
- `chat-integrations-*`, `chatpatches-queue-*`, `chatheads-*`: optional-mod contracts and specific queue/decorating APIs.
- `click-filter-*`, `click-screen-*`: protection at the native command-click entrypoint.
- `commands-*`, `client-commands-*`, `identifier-*`: permission and resource identifier APIs.
- `metadata-*`, `mixin-config-*`: release metadata and injection lists.

Try existing combinations first when adding a target. Add an adapter only for a verified interface difference. Select exactly one implementation of each class; keep tests alongside the interface they exercise. Do not merge unrelated version branches merely to reduce directory count. Native text identity remains version-specific even where codec source is shared.

Use `./gradlew -PgameTargets=1.20,1.21 build` to configure only selected targets and common. Omitting the property builds every registered target. Compilation does not establish release-JAR Mixin correctness or live compatibility.

26.1–26.3 share the modern network, text, client and integration adapters where their actual APIs match; metadata is expanded per target. Each target retains an independent native text encoding identity.
