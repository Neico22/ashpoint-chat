AshPoint Chat 1.0.1 fixes sudo chat validation and preserves AshPoint Bridge forwarding.

Replace only `ashpoint-chat-1.0.0.jar` with `ashpoint-chat-1.0.1.jar`. Keep AshPoint Bridge 1.6.1 and existing configuration/player data. Restart once.

Sudo uses vanilla server-authored chat with the target's CHAT display name through Fabric's existing player-chat broadcast events. No forged player signature, fake inbound packet, global secure-chat change, or second Discord forwarding path. Carpet fake players and real targets receive safe server-authored messages; real-target sudo cannot be genuinely signed by that player's client or cryptographically reportable as their authored chat.

Tested the exact attached production JAR on Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25, Carpet 26.3+v260915, unmodified Bridge 1.6.1, with and without LuckPerms 5.5.85. 92 integration assertions passed in each full run, plus 5 actual restart assertions. Checked normal connected recipients, fake/real sender attribution, exactly one delivery, Bridge's actual event listener and formatting, muted/ignored chat, normal chat and target-permission commands. Existing chat features remain intact; no CommandSpy added.

Discord submission was captured at the production Bridge/JDA boundary; no live Discord delivery or graphical online-authenticated client session was tested. See TESTING.md for scope and evidence.

SHA-256: `15f047a18cfc23c2d1eae8fbfe95ba7d45d8bb8d9396e8e47cce3320400e4674`.
