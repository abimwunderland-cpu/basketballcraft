# BasketballCraft v0.8 (Paper 1.21.11)

## Neu
- Ausdauer (100 Punkte), Sprintverbrauch, Regeneration, Sprint-Sperre bis 25 Punkte
- Manueller Dunk: Sprinten, springen, innerhalb 12 Ticks Linksklick in Korbnähe (max. 3,5 Blöcke, max. 38°); mindestens 70 Ausdauer. Dunk kostet 35, zählt 2 Punkte.
- Ball beim Dribbeln weiter vorn und näher zur Mitte
- Zielhilfe auf maximal 12° begrenzt; selbst Green Release korrigiert falsches Zielen nicht vollständig
- V0.7 Smart-Pass, Pickup und Wurfmeter bleiben erhalten; keine Jerseys

## Installation
1. ZIP-Inhalt ins GitHub-Repository hochladen, GitHub Actions `Build BasketballCraft` ausführen.
2. `basketballcraft-0.8.0.jar` aus dem Action-Artefakt in den plugins-Ordner kopieren. Alte Version entfernen.
3. Resourcepack-ZIP clientseitig aktivieren.
4. Server neu starten. `/bc ball`, `/bc sethoop red|blue`, `/bc showhoops`.

## Testhinweise
- Die Dunk-Animation ist eine kontrollierte Velocity-Bewegung, kein Teleport. Minecraft-Kollisionen können Dunks verhindern.
- Die Dunk-Erkennung nutzt die markierte Ringmitte. Hoops müssen exakt gesetzt sein.
- Falls eine ältere `plugins/BasketballCraft/config.yml` existiert, neue Werte manuell übernehmen oder vor Neustart sichern und neu generieren lassen. Die Zielhilfe wird im Code auf 12° begrenzt.
- Wurfphysik ist aus v0.7 übernommen; Trefferverhalten und Sprungimpuls bitte auf der eigenen Map testen.
- Das Ballmodell ist weiterhin das bisherige V0.7-Modell, nicht neu designt.
