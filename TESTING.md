# AshPoint Chat 1.0.0 validation

Test environment: Minecraft 26.3 dedicated Fabric server, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25.0.4.1, Carpet 26.3+v260915. LuckPerms run used Fabric 5.5.85. Tests used isolated offline-mode worlds bound to localhost, not the live AshPoint server.

| Run | Result |
|---|---|
| Production JAR with Fabric API only: startup, info, shutdown | Pass |
| Dedicated server with LuckPerms | 75 passing assertions |
| Actual shutdown and restart, same saved world | 5 passing persistence assertions |
| Dedicated server without LuckPerms | 75 passing assertions |

The interaction runs each used three real Carpet fake-player entities plus two independently connected socket clients speaking vanilla Minecraft's network protocol. The clients installed no mod. Client packet receipt was asserted, rather than relying only on server console chat text. Tests are in `src/test/java`, and are packaged in a separate test mod excluded from the production JAR.

Verified: mod/config initialization; formatting disabled by default; all command registrations; LuckPerms permissions and normal-player sudo denial; sudo command execution using target identity/permissions; sudo chat through Carpet/player-chat packets; private-message aliases; replies; message-toggle rejection; ignore filtering of public and private messages; SocialSpy delivery; permanent and temporary mutes, expiry, unmute, muted public/DM blocking; chat lock and bypass; slow chat; anti-spam rapid/repeated messages, character repetition, CAPS and near duplicates; staff-mode routing; mention sounds/toggles; targeted clear chat; invalid JSON reload rejection and valid reload; duration validation; info; disk persistence and actual restart persistence; connected-player chat and DMs; sudo chat targeting a connected player. The same interaction suite passed with LuckPerms removed and operator fallback active.

Real connected clients were headless protocol clients in offline mode, not human-operated graphical or online-authenticated Minecraft clients. Signed-chat cryptographic authentication, graphical rendering, and client scrollback deletion were not tested. Clear chat does not delete scrollback; sudo chat is unsigned. Optional custom formatting and every possible anti-spam/config combination were not exhaustively tested. The specific installed Essentials JAR and other AshPoint mods were not provided for this project, so exact-stack coexistence has not been verified. Chat alias ownership and fallback commands are documented in README.md.

Reproduce with Java 25:

```sh
./gradlew build runtimeTestJar
python3 tests/run_server_tests.py
python3 tests/run_server_tests.py --restart
python3 tests/run_server_tests.py --no-lp
```

The runner uses only its isolated `build/runtime-test-server/` directory and accepts the Minecraft EULA for that test server. Never install `ashpoint-chat-test-harness-1.0.0.jar` on a live server; it performs tests and stops its test server automatically. Gradle's standalone `test` task is disabled because these tests require a running Minecraft/Fabric server. A passing Gradle build alone is not the interaction test result.

GitHub release v1.0.0 is published at https://github.com/Neico22/ashpoint-chat/releases/tag/v1.0.0. The uploaded JAR was downloaded and its SHA-256 verified against the exact tested artifact. The included workflow runs the dedicated-server suite for version tags and preserves an existing manually published release.

Exact tested and delivered production JAR SHA-256: `3b082b28e3c66828f24ee7b71ce54390a69580dd456d9a730c19b9f6e21915b5`.
