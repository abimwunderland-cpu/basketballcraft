# BasketballCraft 1.0.0 – Event Edition (Paper 1.21.11)

Source package based on the working 0.8.8 codebase. Java 21 / Paper 1.21.11. GitHub Actions builds the JAR; the ZIP itself is source, **not** a ready-to-install JAR. Existing 0.8.x resource pack can stay installed.

## Installation

1. Back up your server and `plugins/BasketballCraft/config.yml` (and keep the working 0.8.8 JAR for rollback).
2. Upload source contents to a GitHub repository; Actions → Build → download the JAR artifact.
3. Stop the Paper 1.21.11 server, replace old JAR, start server.
4. Test on a **separate test server** before using in an event. Java compilation and Minecraft runtime were not verified in this environment.

## Court setup

- `/bc setup` opens the existing editor. Set arena corners, both hoop rings, three-point lines and team spawn positions.
- `/bc arena save <name>` saves after BOTH corners were marked; `/bc arena select <name>` chooses the active arena.
- `/bc spawn add 1` and `/bc spawn add 2` create multiple spawn points for each team, including facing direction.
- `/bc spawn ball` sets the center ball spawn (required for matches).
- `/bc spawn list`, `/bc spawn show`, `/bc three show`, `/bc showhoops` help check setup.

**Important mapping:** Red hoop = Team 1's own/defended basket; Blue hoop = Team 2's own/defended basket. A live match awards points to the *opposite* team of the basket being scored in. Teams are controlled by the admin.

## Event workflow

```
/bc team add PlayerA 1
/bc team add PlayerB 2
/bc team list
/bc match time 5
/bc match start
/bc match status
/bc match pause
/bc match resume
/bc match stop
```

- `/bc team remove <player>`, `/bc team clear` (only outside matches)
- `/bc spectate` toggles admin spectator mode (restores previous game mode)
- `/bc stats [player]` shows current match points, assists, steals and blocks
- `/bc help 4` lists new commands; `/bc help 1-3` list old commands
- `/bc score` shows team score

No hard-coded team size: supports 1v1 to 5v5 provided sufficient spawns and court space. Admin starts and stops the game manually. Default duration: 5 minutes. 5-second countdown. A player disconnecting pauses the match until an admin resumes it.

## Defense

- **Steal:** aim at an opposing ball carrier within 2 blocks and left-click. Requires proper facing, cooldown, and a short possession-protection window. Also supports direct right-click on a player.
- **Shot block:** jump into the ball's flight path; defensive interception requires being airborne and moving upward.
- **Dunk block:** defender jumps near dunker in time to interrupt the dunk; the ball is released.
- Smart-pass in a live match aims only at teammates.

## Known limitations / verification needed

- This is a **test candidate**, not a certified production release. Maven/Java compilation and Paper runtime tests have **not** been performed here.
- The plugin architecture from 0.8.8 stores a single active ball, court and game server-wide; multiple simultaneous arenas are not supported.
- The original arcade shot physics and stamina are preserved. Shot difficulty is not yet fully rebalanced.
- Match state, assignments and player stats are **in-memory** and reset on restart; arena/court settings remain in config.
- No overtime, fouls, shot clock, tournament bracket, spectator camera automation or persistent leaderboards.
- Admin should test team scoring (correct basket), blocking, steals, reconnect/pause, match countdown, scoreboard, and ball resets before an event.
- PvP damage between participants is cancelled during a match; other installed plugins may interfere with movement or interactions.
