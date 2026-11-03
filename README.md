# BasketballCraft 0.8.1 — Paper 1.21.11

Source project with GitHub Actions. Java 21 / Maven / Paper API 1.21.11.

## Neu
- Dynamisches Green-Release-Fenster: 70–100 Stamina = 8 Ticks, 40–69 = 6, 20–39 = 5, 0–19 = 4. Green-Zone ist im Actionbar-Meter sichtbar.
- Zielhilfe nur bei echtem Blick auf den Ring (horizontal UND vertikal), maximal 7 Grad.
- Sprint-Sperre bei Erschöpfung; Freigabe ab 25 Stamina. Regeneration beim Gehen und Stehen.
- Dunk trennt den Ball vom Spieler und lässt ihn durch den Ring fallen; Anzeige +2.
- Normale Körbe lassen den Ball mit seiner Fluggeschwindigkeit weiterfallen.
- Ball-Display 15 % größer (gleiches Modell, Resourcepack muss bei unveränderten Texturen nicht zwingend neu aktiviert werden).
- Korb-Wands: `/bc wand 1` (rot), `/bc wand 2` (blau). Rechtsklick Block = Ringmitte im Blockzentrum; Shift+Rechtsklick = Korb entfernen.
- Hallen-Wand: `/bc wand arena` (Holzaxt). Linksklick = Ecke 1, Rechtsklick = Ecke 2.
- `/bc arena save <name>`, `/bc arena show`, `/bc arena list`, `/bc arena select <name>`, `/bc arena delete <name>`.

## Installation
1. ZIP entpacken und Inhalt in GitHub-Repository hochladen.
2. GitHub Actions → Build BasketballCraft → Run workflow.
3. JAR-Artefakt `basketballcraft-0.8.1.jar` herunterladen und alte JAR im Server-Ordner `plugins/` ersetzen.
4. Paper 1.21.11 mit Java 21 neu starten.
5. Resourcepack (separates ZIP) clientseitig aktivieren, sofern noch nicht vorhanden.

## Wichtige Hinweise
- Die Hallengrenze wird gespeichert und angezeigt, aber Out-of-Bounds ist noch nicht implementiert.
- Die zwei Körbe sind aktuell global pro Server; eine automatische Zuordnung zu verschiedenen Hallen ist noch nicht implementiert. Die aktive Halle ist als Grundlage für künftige Matches gespeichert.
- Der Dunk und die Ballphysik müssen ingame geprüft werden. In dieser Umgebung wurde kein vollständiger Paper-API-Build oder Server-Test durchgeführt.
- Die Ausdauer-Sperre verwendet `Player#setSprinting(false)`; das Serververhalten sollte mit dem Client getestet werden.
