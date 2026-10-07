# AshPoint Chat 1.0.1

Server-only chat moderation and messaging for Minecraft **26.3**, Java **25**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**. LuckPerms Fabric 5.5.x and Carpet are optional production integrations. Clients install nothing.

Install `ashpoint-chat-1.0.1.jar` in `mods/`, with Fabric API. Restart once to install the mod. Afterwards use `/ashpointchat reload` to reload its JSON configuration.

## Commands and permissions

All permission nodes start with `ashpoint.chat.`. LuckPerms explicit grants/denials take priority; undefined nodes use the defaults below. With LuckPerms absent, player defaults and operator level 2+ apply. Console can use admin commands. Player preference/DM commands require a player source.

| Command | Permission suffix | Default |
|---|---|---|
| `/clearchat [player]` | `clearchat` | OP |
| `/sudo <player> command <command>` | `sudo` | OP |
| `/sudo <player> chat <message>` | `sudo` | OP |
| `/mute <player> [duration] [reason]` | `mute` | OP |
| `/unmute <player>` | `unmute` | OP |
| `/mutelist` | `mutelist` | OP |
| `/ignore <player>`, `/unignore <player>`, `/ignorelist` | `ignore` | Everyone |
| `/msg <player> <message>`; `/tell`, `/w`, `/whisper` | `msg` | Everyone |
| `/reply <message>`; `/r` | `msg` | Everyone |
| `/msgtoggle` | `msgtoggle` | Everyone |
| `/mentiontoggle` | `mentions` | Everyone |
| `/socialspy` | `socialspy` | OP |
| `/staffchat [message]`; `/sc` | `staffchat` | OP |
| `/slowchat <seconds>`; `/slowchat off` | `slowchat` | OP |
| `/lockchat [reason]`; `/lockchat off` | `lockchat` | OP |
| `/ashpointchat reload` | `reload` | OP |
| `/ashpointchat info` | `info` | Everyone |

| Bypass/notification permission suffix | Effect | Default |
|---|---|---|
| `clearchat.bypass` | Receives clear notice without blank lines | OP |
| `mute.bypass` | Chat and DM despite mute | OP |
| `slowchat.bypass` | Bypass slow chat | OP |
| `lockchat.bypass` | Bypass chat lock | OP |
| `antispam.bypass` | Bypass anti-spam | OP |
| `antispam.alerts` | Receive anti-spam alerts | OP |
| `msg.bypass` | Bypass recipient message toggle when configured | OP |

For example: `/lp group moderator permission set ashpoint.chat.mute true`. Denying a bypass node also denies it for an operator.

Mute durations: positive whole numbers with `s`, `m`, `h`, `d`, `w`, up to ten years. Examples: `/mute Steve 10m Spam`, `/mute Steve 2h Advertising`. No duration defaults to permanent. To moderate offline players, use a previously seen player name or exact UUID.

SocialSpy sees successful AshPoint private messages, including all aliases and replies. Sender and recipient do not receive duplicate spy copies. Message toggles reject incoming messages; ignoring blocks both directions of private conversation. Ignore applies to public player chat, never server/system moderation notices. Staff mode and spy permissions are checked again each time messages are sent, so revoking permission takes effect immediately.

## Chat and command compatibility

AshPoint intentionally owns its listed chat commands, including vanilla `/msg`, `/tell` and `/w`. It logs detected conflicts and reinstalls those branches after startup/datapack reload. Disable competing chat command modules if they take the same aliases. Every command is also available underneath `/ashpointchat`, e.g. `/ashpointchat msg Steve hello`, providing an unambiguous AshPoint path. Unrelated Essentials commands and CommandSpy are untouched. No ban, kick, teleport, vanish or general Essentials replacement features are added.

**Formatting is disabled by default.** Normal unmodified messages retain the vanilla player-chat pipeline, sender UUID and existing chat decoration. Mentions highlight exact case-sensitive `@PlayerName` tokens for the mentioned recipient and can send a vanilla note-block sound. `/mentiontoggle` disables that player's mention highlights/sounds. Spy, staff and system messages do not trigger mentions.

When optional `formatting` is enabled, public messages are delivered as formatted system messages using `publicFormat` and LuckPerms prefixes. This loses client signed-chat presentation/reporting for those deliveries and may conflict with another formatter; enable only if AshPoint is to handle your formatting. Prefix text is plain text, not MiniMessage markup.

`/sudo ... chat` uses vanilla **server-authored chat**, bound to the target’s normal `CHAT` display name, through `PlayerList.broadcastChatMessage(message, target, type)`. Fabric’s `ALLOW_CHAT_MESSAGE` and `CHAT_MESSAGE` events receive the actual target player once. Default delivery uses vanilla disguised chat, which has no cryptographic player-signature/session-chain fields. It does not forge a signature, feed a fake inbound client packet, disable secure-chat enforcement, use tellraw, or broadcast raw system text to every player. Mutes, slow chat, chat lock, anti-spam, staff mode, ignores, mentions and optional formatting still apply.

Carpet 26.3+v260915 has no separate fake-player chat-signing method. `EntityPlayerMPFake` uses `NetHandlerPlayServerFake` and `FakeClientConnection`; shadow players can inherit a real player’s chat session. No Carpet/client private signing key exists for the server to use. Consequently sudo uses the server-authored path for both fake and real targets. For real players these messages are **not genuinely signed or attributable to their client in cryptographic chat reporting**; they are visibly attributed by vanilla’s chat-type name. Their genuine incoming chat pipeline is unchanged.

AshPoint Bridge 1.6.1 already listens to `ServerMessageEvents.CHAT_MESSAGE`, checks `ChatType.CHAT`, then calls its existing `sendPlayerChat` with the supplied target’s UUID, profile name and original text. This fix follows exactly that hook, so Bridge retains its normal player-name/avatar webhook formatting and configured chat/console routing, including multiple guilds. **Keep the existing Bridge 1.6.1 JAR; no Bridge modification is required.**

`/sudo ... command` runs the target’s own command source, identity, position and normal permissions. It never adds OP. Recursion and control characters are rejected, and the target must be online. Logs identify the staff actor, target and command root; AshPoint does not log sudo message bodies or private-message text.

Vanilla clients have no server-side instruction for deleting arbitrary chat history. `/clearchat` sends configurable blank lines to push history out of view, followed by a notice. It cannot delete messages from logs or prevent scrolling back.

## Configuration and data

Configuration: `config/ashpoint-chat.json`, generated with readable defaults. Player/moderation data: `<world>/ashpoint-chat-data.json`, keyed by UUID. Back up that data file with your world. Preferences, ignores, mutes/expiry/reasons, spy/staff/mention/message toggles, slow chat and chat lock persist. Reply targets are intentionally session-only.

Data saves write a temporary file, force its bytes to disk and atomically replace the previous snapshot where supported. Unreadable startup data/config is rejected instead of overwritten. Invalid reload keeps the running configuration and all player data. Expired mutes are removed every second and on attempted chat.

Main configuration keys:

- `formatting`: false; `publicFormat`: `{prefix}{sender} » {message}`.
- `privateFormat`, `spyFormat`, `staffFormat`: message templates with `{sender}`, `{target}`, `{message}` as appropriate.
- `staffMessageBypass`: true; permission required to bypass a disabled incoming-DM toggle.
- `mentionSound`: true; default sound is vanilla note-block pling. `mentionSoundId`, `mentionVolume` and `mentionPitch` are configurable.
- `defaultMuteSeconds`: 0 means permanent.
- `clearLines`: 100 (allowed 1–1000).
- `muted`, `locked`, `slow`, `spam`, `clearNotice`, `ignored`, `messagesOff`: configurable main notices, using `{remaining}`, `{reason}`, `{sender}` where appropriate.
- `antiSpam`: true; default actions `cancelSpam`, `warnSpam`, `alertStaff` true; `autoMute` false.
- `minIntervalMillis`: 900; `duplicateWindowSeconds`: 15; `maxCharacterRepeat`: 12.
- `capsMinimumLetters`: 12; `capsPercent`: 85.
- `violationsToMute`: 5; `violationWindowSeconds`: 60; `autoMuteSeconds`: 300.

Near-duplicate detection normalizes punctuation/spacing/case and compares edit distance for messages of at least eight letters/digits by default (`nearDuplicateMinimumLength`, `nearDuplicateDistance` are configurable). Anti-spam applies to public player messages, including sudo chat; it does not process commands, staff mode, private messages or system messages. Anti-spam cancellation defaults are conservative; adjust thresholds to suit your server.

## Build

Install Java 25, then run `./gradlew build`. The complete Gradle wrapper is included. Minecraft 26.3 ships readable names, so compilation uses the pinned official server libraries directly; no remapping is needed. Dependencies are downloaded and SHA-256 verified by `prepareGameClasspath`. Optional integrations are not bundled in the JAR. Output: `build/libs/ashpoint-chat-1.0.1.jar`.

Tests use a separate server-only harness JAR that must **never** be installed on production. See `TESTING.md` for actual test results and limitations.
