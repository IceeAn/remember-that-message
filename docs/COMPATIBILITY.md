# Version and integration matrix

Twenty separate targets cover release versions 1.20–1.20.6, 1.21–1.21.11 and 26.3, excluding snapshots and prereleases. Matching vanilla clients can join a server with the mod installed. Dependencies and source selection are declared in `versions/<version>/target.properties`.

| Minecraft | Java | Fabric API | Chat Patches | Chat Tools | Chat Heads |
| --- | --- | --- | --- | --- | --- |
| 1.20 | 17 | 0.83.0+1.20 | 8.0.0+1.20.1-fabric | 2.4.5 | 0.15.7 |
| 1.20.1 | 17 | 0.92.12+1.20.1 | 8.0.0+1.20.1-fabric | 2.4.5 | 0.15.7 |
| 1.20.2 | 17 | 0.91.6+1.20.2 | — | 2.4.5 | 0.15.6 |
| 1.20.3 | 17 | 0.91.1+1.20.3 | — | 2.4.5 | 0.15.6 |
| 1.20.4 | 17 | 0.97.3+1.20.4 | — | 2.4.5 | 0.15.6 |
| 1.20.5 | 21 | 0.97.8+1.20.5 | — | 2.4.5 | 0.15.6 |
| 1.20.6 | 21 | 0.100.8+1.20.6 | — | 2.4.5 | 0.15.6 |
| 1.21 | 21 | 0.102.0+1.21 | 8.0.0+1.21.1-fabric | 2.4.5 | 0.15.7 |
| 1.21.1 | 21 | 0.116.17+1.21.1 | 8.0.0+1.21.1-fabric | 2.4.5 | 0.15.7 |
| 1.21.2 | 21 | 0.106.1+1.21.2 | — | 2.4.5 | 0.15.6 |
| 1.21.3 | 21 | 0.114.1+1.21.3 | — | 2.4.5 | 0.15.6 |
| 1.21.4 | 21 | 0.119.4+1.21.4 | — | 2.4.5 | 0.15.6 |
| 1.21.5 | 21 | 0.128.2+1.21.5 | — | 2.4.5 | 0.15.6 |
| 1.21.6 | 21 | 0.128.2+1.21.6 | — | 2.4.5 | 0.15.6 |
| 1.21.7 | 21 | 0.129.0+1.21.7 | — | 2.4.5 | 0.15.6 |
| 1.21.8 | 21 | 0.136.1+1.21.8 | — | 2.4.5 | 0.15.6 |
| 1.21.9 | 21 | 0.134.1+1.21.9 | — | 2.4.5 | 1.3.2 |
| 1.21.10 | 21 | 0.138.4+1.21.10 | — | 2.4.5 | 1.3.2 |
| 1.21.11 | 21 | 0.141.6+1.21.11 | 8.0.0+1.21.11-fabric | 2.4.5 | 1.3.2 |
| 26.3 | 25 | 0.165.0+26.3 | 8.0.0+26.3-fabric | 2.4.5+26.3 | 1.3.2-fabric-26.3 |

`—` means Chat Patches integration is not provided for that target. Its alpha releases lack the queue interface required by the existing adapter. Structural rejection and status reporting remain; basic history stays available. Time/head integrations combined with those Chat Patches releases are also outside the supported scope. Chat Tools alone, Chat Heads alone and their combination are checked separately.

Third-party versions identify specific validation candidates, not an automatic guarantee for future releases. On 1.21, the latest YACL requires a newer compatible Fabric API; checks use 0.116.17+1.21.1, whose metadata permits >=1.21- <1.21.2-. The base mod retains the lower minimum listed above.

Each target produces `versions/<version>/build/libs/server-chat-history-0.2.0+mc<version>.jar`. Install only its matching release JAR; `-sources.jar` is for development. All 1.20/1.21 release artifacts are remapped to intermediary names.

Sources are explicitly composed by API capability; see the [adapter guide](../platform-adapters/README.md). 1.20–1.20.4 use raw channels; 1.20.5 onward use typed payloads. Native text identifiers remain specific to actual game versions: shared Java APIs do not imply registry compatibility. Same-version data avoids conversion; different versions use portable conversion with reported readable-content degradation.

See the [acceptance checklist](TESTING.md) for build, JUnit and release-JAR headless scope. Headless checks load real classes, apply Mixins and call tested methods without starting game entrypoints, worlds, networking, windows or audio. Rendering, live connections and cross-version proxy switching still require separate acceptance.
