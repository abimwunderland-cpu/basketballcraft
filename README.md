# BasketballCraft V0.7 (Paper 1.21.11)

**Gameplay test build**. Source based on V0.6; original shooting and hoop detection retained. Jerseys removed.

## Installation
1. Upload repository contents to GitHub (including `.github/workflows/build.yml`).
2. GitHub Actions > Build BasketballCraft > Run workflow, download JAR from Artifacts.
3. Stop server, remove old BasketballCraft JAR, install new JAR, restart.
4. Install `BasketballCraft-v0.7-Resourcepack.zip` in client resourcepacks, enable and F3+T.
5. `/bc ball` creates a single ball; `/bc sethoop red|blue` marks hoop center; `/bc showhoops` shows hoop regions.

## Controls
- Walk into free ball to take possession.
- With possession: left click to start shooting meter, left click again to release.
- Right click: Smart Pass (aim within 12 degrees of another player within 16 blocks, else free pass).
- Sneak + right click: put ball down.
- Dribbling is automatic and displayed to the player's right.
- Passing ball can be intercepted. Pickup lock 12 ticks prevents immediate recapture by passer.

**Note:** No Q-to-drop support because the ball is not an inventory item. Sneak + right click is the drop action.
**Note:** Until teams are added, smart targeting may select an opponent in the crosshair; it does not know teams.
**Note:** This version has not been tested inside a Minecraft server; use GitHub Actions to validate compilation.
