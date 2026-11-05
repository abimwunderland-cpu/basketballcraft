# BasketballCraft 2.0 Beta – Development source

## Implemented in this revision
- Persistent all-time Points, Wins, Steals, Blocks in `plugins/BasketballCraft/leaderboard.yml`. Previous points/wins preserved. New lifetime steals/blocks start from zero because older versions did not persist them.
- `/bc board set` creates a floating TextDisplay near the admin looking direction; 4 category leaderboards, top three each, periodic updates every 5 seconds. `/bc board refresh`, `/bc board remove`. Display is NOT a true 4x4 block map wall. Size depends on text/font and scaling.
- `/bc npc spawn coach|teams|stats`, `/bc npc list`, `/bc npc remove` (nearest within 5 blocks). Right click opens an interactive inventory guide; stored in config. NPCs are VILLAGERS, not custom player-skin NPCs. Custom player skins need a separate implementation or Citizens integration.
- Matchup intro titles and winner announcement.
- Retains existing v1.8 game mechanics and audio resourcepack.

## Important limits
- NOT compiled or runtime-tested. Requires Maven + Java 21 + Paper 1.21.11 API; build via included GitHub Actions workflow.
- NPC custom player skins, a true 4x4 block/map display, and an in-game end-to-end test are not completed. Do not replace a live plugin without backups and testing.
- Statistics for steals/blocks prior to this update cannot be recovered from the old leaderboard.yml automatically; match-history.yml might contain old per-match records, but no migration is included.
- `/bc board set` positions a display about 3 blocks ahead. Place while facing intended viewing direction; remove/re-set if needed.
- Resourcepack in companion ZIP is the previous V1.8 pack, unchanged. No new player skin or GUI textures.
