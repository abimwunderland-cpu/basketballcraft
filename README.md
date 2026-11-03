# BasketballCraft V0.5 — test build

Paper 1.21.11 / Java 21. Build: GitHub Actions or `mvn clean package`.

- `/bc ball` creates the ball; left click starts meter, second left click releases.
- `/bc sethoop red|blue` saves rim center at eye position; `/bc showhoops` displays scoring rings.
- Shots aimed within 28 degrees of a configured hoop receive a physically simulated arc; the shot can still miss.
- `/bc jersey` EQUIPS the chestplate and leggings into the armor slots, returning old armor to inventory.
- Use accompanying V0.5 resourcepack ZIP in Minecraft resourcepacks; place above other packs.

**Limitations:** This is a beta. Hoop detection still requires a downward crossing of the rim plane, and the custom armor uses vanilla armor geometry, not articulated 3D clothing. The aiming assist is an approximation, not guaranteed physics. Actual in-game test required.
