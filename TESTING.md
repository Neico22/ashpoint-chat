# AshPoint Chat 1.0.1 verification — 2026-10-07

The exact production JAR ran on an isolated Minecraft 26.3 dedicated Fabric server: Loader 0.19.5, API 0.161.0+26.3, Java 25.0.4.1, Carpet 26.3+v260915, optional LuckPerms Fabric 5.5.85, and unmodified AshPoint Bridge 1.6.1. The server was offline-mode, localhost-only, with `enforce-secure-profile=true` retained. No live server files or Discord credentials were used.

| Run | Result |
| --- | --- |
| Full server with LuckPerms and production Bridge | 92 assertions passed |
| Actual shutdown/restart of the same saved world | 5 persistence assertions passed |
| Full interactions after restart, without LuckPerms, with production Bridge | 92 assertions passed |

Each full run spawns three actual Carpet entities via `EntityPlayerMPFake.createFake` and connects two independent vanilla-protocol socket clients. Carpet connections are captured by the separate test harness; normal recipients are independently decoded from the network stream. The release contains only main classes/resources; neither test harness nor Bridge is bundled.

## Required sudo/Bridge checks

| Requirement | Evidence |
| --- | --- |
| Spawn fake player, sudo chat | Actual Carpet ChatAlice; `/sudo ChatAlice chat hello from bot` |
| Correct sender, visible once in Minecraft | One vanilla disguised-chat packet with CHAT name ChatAlice and exact text decoded by connected WireBob; fake recipients likewise receive once |
| No invalid signed-chat path | Sudo uses ClientboundDisguisedChatPacket, never ClientboundPlayerChatPacket; packet has no signature/session/link fields to validate |
| No validation disconnect/error | Both socket clients stay connected without failure; logs contain no Chat Validation Error |
| Actual Bridge receives chat | Production 1.6.1 registers its actual Fabric CHAT_MESSAGE listener; test installs only a configured DiscordBridge instance in that mod |
| Discord forwarding exactly once | Production sendPlayerChat/sendPlayerMessage execute; JDA webhook queue submissions captured once per public channel, plus existing console mirrors, across two guilds |
| Correct Discord sender/message | Assert original profile name, exact text, UUID in normal Mineatar avatar URL, disabled mentions and four distinct destinations (chat + console for each guild) |
| No duplicates | Exact Minecraft packet and Bridge submission/destination counts; no second compatibility forwarder |
| Mutes | Muted fake target produces zero Minecraft chat and Bridge submissions; temporary mute/expiry, unmute and muted DM also pass |
| Ignores | Fake and normal connected ignoring recipients receive no sudo chat; other recipients and Bridge receive once |
| Normal chat | Connected-player inbound chat produces one Minecraft delivery and one Bridge submission per destination with original username/UUID/text |
| Target command permissions | Target sends DM/reply under its UUID; non-OP target cannot execute gamemode despite console issuing sudo |
| Restart | Mute/reason, ignore, SocialSpy/staff/message/mention preferences survive actual restart; full interactions then pass again |
| Real connected target | WireAlice attribution/text arrive once as server-authored chat and once in each Bridge destination |
| Optional custom formatting | One formatted system delivery, zero duplicate chat packets; Bridge receives original player/chat event once |

Additional regression coverage: all commands/DM aliases, LuckPerms grants/denials and OP fallback, replies, message toggle, SocialSpy, staff mode, slow chat, lock/bypass, anti-spam speed/repeat/near-duplicate/CAPS/repetition, mentions/toggles, clear chat, valid/invalid config reload, durations and persistence. No CommandSpy added.

## Root cause and inspected implementations

Previously PlayerChatMessage.unsigned(targetUUID, text) caused vanilla OutgoingChatMessage.create to select OutgoingChatMessage.Player, transmitting a signed-player chat envelope without a legitimate target signature/session chain. Client validation can reject it, especially for signed-session targets or when no initialized chat session is accepted. Old 1.0.0 tests decoded receipt but did not prove client signed-chat acceptance.

The pinned Carpet JAR bytecode shows fake entities use NetHandlerPlayServerFake and FakeClientConnection; there is no fake-player chat-signing API/private key. Shadow creation copies a real player's remote chat session. Feeding handleChat manufactured inbound packets does not solve signature ownership/session validation and is not used.

The fix uses PlayerChatMessage.system(text) with the actual target argument and ChatType.bind(ChatType.CHAT, target) in the existing public PlayerList chat broadcast. Vanilla selects OutgoingChatMessage.Disguised. Fabric's message API mixin invokes ALLOW_CHAT_MESSAGE followed by CHAT_MESSAGE once for this overload. Scoped, bound-type-specific metadata preserves target UUID for ignores, mentions and formatting; it is restored/cleared in finally and never used as cryptographic sender identity.

Bridge's current GitHub main contains only README.md, with no other branches/releases. Current Bridge 1.6.1 binary was authoritative for inspection/testing. The 1.6.0 source archive was a readable reference only, never rebuilt/substituted. Bytecode confirms 1.6.1 checks CHAT type, uses supplied sender UUID/profile name, reads message.signedContent() and calls its existing formatter/router. No Bridge binary, configuration, linking data or functionality changed.

## Limits

Real targets were headless vanilla-protocol clients in offline mode, not graphical or online-authenticated clients with Mojang-certified keys. Graphical rendering and secure online authentication were not executed. Sudo transport is server-authored and contains no player signature/session chain, so it cannot enter player-message cryptographic validation. Normal signed inbound chat code was unchanged. The server cannot create a real player's genuine signature; sudo is not cryptographically reportable as that player's client-authored chat.

Discord tests capture production webhook submission after formatting; they do not post to live Discord. Live API delivery, bot/channel permissions and outages were not tested. The complete live mod stack was not supplied, so coexistence beyond tested versions is not claimed. Clear chat pushes messages out of view rather than deleting scrollback.

## Developer reproduction

`./gradlew build runtimeTestJar` prepares the checksum-pinned classpath and compiles Java 25 sources. A network-independent equivalent after preparation is `JAVA_HOME=/path/to/jdk25 python3 tests/build_cached.py`; it validates pinned inputs and reconstructs nested classpath JARs before compiling/packaging. This release used that equivalent build; exact output bytes were installed in every test server.

Set ASHPOINT_BRIDGE_JAR to production Bridge 1.6.1 and run `python3 tests/run_server_tests.py`, `--restart`, then `--no-lp`. Without that fixture, CI runs Chat regression tests and omits Bridge assertions. Never install the test harness on a live server: it changes test state and stops its test server.

Production Chat SHA-256: `15f047a18cfc23c2d1eae8fbfe95ba7d45d8bb8d9396e8e47cce3320400e4674`.

Tested unmodified Bridge 1.6.1 SHA-256: `0bffe56e9a815618d104859ca0bfa3e6e244be762944f08f3871f6db864e42b5`.
