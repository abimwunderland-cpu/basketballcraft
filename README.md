# BasketballCraft v0.8.7 – Fatigue while stamina is falling

Based on v0.8.6. No resource pack changes.

## Fix
- Sprint is now cancelled whenever stamina is <=30, including on the way DOWN, not only during recovery.
- At 6–30, reduced walk speed applies while stamina decreases AND increases.
- At 0–5, horizontal movement remains locked.
- Regeneration continues to require standing still.
- Exhaustion sprint lock is retained until 30 stamina.

## Test
Sprint from 100 down to 30, 20, 5 and 0; verify slowdown BEFORE reaching 0. Release movement keys to regenerate, and verify the same slowdown while stamina rises.

GitHub Actions builds the JAR. This source ZIP has not been compiled or tested on a running Paper server here.
