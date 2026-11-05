# BasketballCraft 2.0 Beta - Purple Neon Court patch

This ZIP is the existing BasketballCraft 2.0 Beta source with an added purple particle boundary. The feature does not require a resource pack; Paper sends the particles to clients.

## Commands
- `/bc arena select <name>` - select existing arena
- `/bc arena bounds 1` - stand at the first floor corner
- `/bc arena bounds 2` - stand at opposite floor corner (sets the precise rectangle)
- `/bc neon on` - enable persistent violet glowing particles around the court
- `/bc neon off` - disable
- `/bc neon status` - display state

Particles are only sent to nearby players every 8 ticks. The setting persists across restarts. No world blocks are changed. Maximum supported width/length 250 blocks. This is a visual rectangle around the existing court, not automatically drawn 3-point/center markings.

This project has NOT been compiled or tested against a Paper 1.21.11 server in this environment. Build with the included GitHub Actions workflow. Backup your old plugin and config first.

Glow upgrade: lavender highlights and sparse END_ROD particles. Visually bright, but particles do not emit actual block light.
