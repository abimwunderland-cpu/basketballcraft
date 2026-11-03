# BasketballCraft 0.2 – Hoop Visualization + Jersey Prototype

Paper 1.21.11 / Java 21 / Maven.

## Commands (admin / OP)
- `/bc sethoop red` and `/bc sethoop blue` — save scoring plane at eye location, show confirmation.
- `/bc showhoops` — toggle red/blue scoring circle particles (admin-only, visible only to you, within 48 blocks). Markers show the **actual** scoring plane and radius 0.55.
- `/bc jersey` — receive burgundy leather chestplate and leggings prototype. **This is not a 3D custom jersey or textured resourcepack yet.**
- `/bc ball`, `/bc start`, `/bc reset`, `/bc score` — as before.

## Build
GitHub Actions -> Build BasketballCraft -> Run workflow -> download artifact.

## Notes
Existing hoop configuration and scoring logic from v0.1 are preserved. Verify scoring and marker alignment in-game. Ball physics are unchanged in this release to avoid regression; improved physics and 3D jersey need separate testing.
