# BasketballCraft 1.8 – League + Arena Audio (SOURCE, unverified)

Built on the user's V1.7 source. **No compiled/tested JAR** is included; build with Java 21 and Maven against Paper 1.21.11.

## Commands (admin)
- `/bc team create Junkies lila` – register a persistent team (one team per color)
- `/bc team create Huan_sohn rot`
- `/bc team add WolfsfellTV Junkies` – persist player's registered team
- `/bc team add Kevinutte Huan_sohn`
- `/bc team list`
- `/bc team remove WolfsfellTV`
- `/bc team delete Junkies` (not when selected)
- `/bc matchup Junkies Huan_sohn` – select sides 1 and 2 and populate active players from registered memberships
- `/bc match start`
- `/bc match pause` / `/bc match resume` / `/bc match stop`
- `/bc music on|off` / `/bc sound on|off` – player preferences for current session

Matchup side 1 is internal `red` and uses first hoop/spawn; side 2 is internal `blue`. Colors of hoop dust and vests derive from selected team colors. Hoop particle rings are shown to all online players during matches. Existing `league` legacy commands are retained for compatibility, but `/bc team` and `/bc matchup` are preferred.

## Audio
Requires matching `BasketballCraft-v1.8-League-Audio-Resourcepack.zip` loaded on clients. Custom synthesized original sounds: score_2, score_3, score_4, dunk, game_music (tip-off), basket_stinger (after scoring), match_pause, match_end. One-shot tracks; no continuous match music. Original custom audio has no samples from NBA broadcasts.

## Important
The source has not been compiled or tested against a live Paper server. No Coach NPCs are included (reserved for next iteration). Existing arena data should be backed up before deploying. There may be edge cases with offline player names, display visuals, and existing legacy team assignments. Test on a staging server.
