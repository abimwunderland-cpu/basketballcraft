# BasketballCraft V1.1 – Event Update (source)

Paper 1.21.11, Java 21. Built on the V1.0 source corresponding to the uploaded basketballcraft-1.0.0.jar. The original ball/resourcepack assets are unchanged.

## Build

Upload the **contents** of this ZIP to GitHub, then run **Actions → Build BasketballCraft**. The workflow runs Maven and uploads `basketballcraft-1.1.0.jar`. This ZIP is source, not an installable JAR. **The Java/Paper build and in-game functionality have NOT been verified in this environment.** Back up your working V1.0 JAR and `plugins/BasketballCraft/config.yml` before testing.

## Event setup

1. `/bc arena select <name>` – select existing arena, or `/bc setup` and `/bc arena save <name>`.
2. `/bc arena bounds 1` – stand at one outside-court corner; `/bc arena bounds 2` – stand at the opposite corner. These define a separate **axis-aligned rectangle** for ball walls and access protection. `/bc arena bounds show` displays positions; `/bc arena bounds clear` reverts to arena corners.
3. `/bc restart set 1` and `/bc restart set 2` – stand at the respective team restart locations. `/bc restart show` verifies them. If missing, the ball spawn is used as fallback.
4. `/bc spawn add 1`, `/bc spawn add 2` – configure multiple team spawn positions; `/bc spawn ball` sets the center ball.
5. `/bc team add <player> <1|2>` – assign teams. `/bc match time 5` then `/bc match start`.
6. `/bc arena lock` – deny unassigned players access while a match is active; admins with `basketball.admin` bypass. `/bc arena unlock` temporarily disables it.
7. `/bc help 5` – in-game help for new commands.

## New mechanics

- **Ball-focused steals**: right-click the ball or right-click while aiming directly at its visible position. 2.5 block range, 0.42 block aim radius, short cooldown. Teammate steals are also allowed. A possession-protection window remains after pickup.
- **No event PvP**: player-vs-player damage cancelled while the match is active.
- **Team vests**: dyed red/blue leather chestplates at match start; previous chestplate restored at match end or disconnect. For safety, avoid manually moving/changing chestplates during matches.
- **Faster stamina regeneration**: 14 points/s standing still (reduced while holding the ball), with no regeneration while moving.
- **Ball-only boundary walls**: simulated bounces along the marked rectangular court edges. No world barrier blocks are placed.
- **Restart after score**: after 35 ticks, ball spawns at the conceding team's restart point and only that team can first pick it up. This applies to normal shots and dunks.
- **Scoreboard**: right sidebar for all online players during matches, score/time, top 3 scorers, Twitch and YouTube lines.
- **Arena access**: spectators without a team are kept outside the marked court while a match is active; admins bypass. This does not restrict flying spectators in spectator mode (they may freely observe).

## Test checklist

- [ ] GitHub Actions successfully compiles with Paper 1.21.11.
- [ ] Aim-at-ball right click steal works from both teams; turning the ball away helps.
- [ ] No PvP damage during active matches.
- [ ] Team vests equip and restore.
- [ ] Ball rebounds at all four court edges, including diagonal passes and high-speed shots.
- [ ] Each scoring basket, including dunk, resets to correct conceding side; opponent cannot first pick up.
- [ ] Unassigned players cannot walk onto the court; admin can enter.
- [ ] Scoreboard updates all players, including spectators.
- [ ] Stamina only regenerates while stationary.

Known scope: one active match/arena per server; outside lines are rectangular rather than arbitrary polygon paths. Team labels are fixed Red/Blue; player stats are per-match. Do not use in a live event before testing.
