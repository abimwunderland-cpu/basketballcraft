# BasketballCraft v0.8.3 — Court Editor & Team Spawns

Paper 1.21.11 / Java 21. Source package with GitHub Actions build.

## Setup
1. `/bc setup` and select Arena Ecke 1, right-click a block with the editor wand.
2. Open `/bc setup` again and select Arena Ecke 2, right-click the opposite corner.
3. Only now run `/bc arena save <name>` (never auto-saved on a single click).
4. In `/bc setup`, choose Team 1 or Team 2 spawn; right-click a block to add one. Repeat for as many player spawns as needed. Shift-right-click removes the last spawn for that team.
5. `/bc spawn add 1` or `/bc spawn add 2` adds a spawn at your exact feet and look direction (recommended).
6. `/bc spawn ball` saves the ball spawn at your feet. `/bc spawn show` previews particles.
7. `/bc spawn list`, `/bc spawn remove 1 2`, `/bc spawn clear 2`.
8. `/bc help` shows in-game command reference.

Team spawn positions are persisted in config.yml, including yaw and pitch. Existing hoops and 3pt lines are unchanged. These markers configure positions; automatic team assignment / match starts are not implemented in this release.

## Build
Push source to GitHub; Actions workflow builds the JAR. Local: `mvn package`. Java 21 and Paper 1.21.11 API required.

## Verification
Archive and source changes prepared, but Java compilation against the Paper API and live gameplay were not verified in this environment. Test on a backup server before replacing production. No new resourcepack required.
