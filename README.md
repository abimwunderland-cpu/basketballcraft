# BasketballCraft v0.1 – First Tip-Off

**Paper 1.21.11 · Java 21 · No client mods**

## Build with GitHub Actions
Upload ALL project files, including the hidden `.github/workflows/build.yml`, to the ROOT of a GitHub repository. Open Actions > Build BasketballCraft > Run workflow. Download the artifact `BasketballCraft-v0.1-JAR`, extract it, and put the JAR into `plugins/`. Restart your Paper server.

The JAR has NOT been compiled or server-tested in this environment. Check the Actions build log for errors.

## Commands
- `/bc ball` – spawn ball at your feet
- `/bc sethoop red` – place the RED scoring center at your current EYE position
- `/bc sethoop blue` – place the BLUE scoring center at your current EYE position
- `/bc start` – spawn ball and begin free play
- `/bc reset` – reset scoreboard and ball
- `/bc score` – show score

## Controls
- Walk into the ball or right-click near it to pick it up.
- Holding ball: left-click to shoot, sneak + left-click to pass.
- While holding the ball, an orange block bobs in front of your feet as a prototype dribble effect.

## Current limitations
- Ball is a scaled orange concrete ItemDisplay, **NOT** a finished 3D basketball model. A resource pack is NOT included.
- Hoop scoring is a mathematical circle at the configured location; no physical rim/backboard collision yet. You must build the hoops yourself and mark their centers while flying.
- Shooting is direction-based; no charge meter, aim assist, or true basketball shooting arcs yet.
- No team ownership, court boundary rules, shot clock, steals, blocks, dunks, or persistent stats yet.
- The first prototype is for functional testing, not competitive play. Collision and pickup edge cases may need tuning.
- `/bc start` is currently free-play ball spawn, not a timed match.
- Existing world geometry can interfere with ball movement. Test on a flat court first.

## Configuration
`plugins/BasketballCraft/config.yml` contains gravity, drag, bounce, friction, throw speeds, pickup radius, and 3-point distance. Hoop positions are saved automatically.

## Credits / licensing
All included Java source is newly authored for this prototype. No third-party models or source code have been bundled.
