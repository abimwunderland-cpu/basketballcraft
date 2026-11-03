# BasketballCraft v0.8.8 – stamina movement fix

Fix: walking now drains stamina, sprinting drains faster, and stamina regenerates **only when standing still**. Actual horizontal movement is tracked using PlayerMoveEvent instead of relying solely on velocity or sprint state. This also applies when stamina drops below 30.

- Sprint without ball: 5/s; with ball: 9/s
- Walk without ball: 2.5/s; with ball: 3.5/s
- Stand still: +9/s (with ball: x0.6)
- 0–5: horizontal movement blocked; 6–20: heavy slowdown; 21–30: mild slowdown
- Sprint blocked at <=30, with exhaustion unlock at 30

All other systems retained from v0.8.7. No resourcepack changes.

## Build
GitHub Actions workflow included. Java 21, Paper 1.21.11. Build not verified locally.
