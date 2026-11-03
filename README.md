# BasketballCraft V0.8.2 – Paper 1.21.11

## Court-Editor
`/bc setup` öffnet das Inventarmenü. Wähle Halle Ecke 1/2, Korb Team 1/2 oder Dreierlinie Team 1/2. Du erhältst den goldenen Editor-Stab. Rechtsklick auf einen Block setzt den Punkt, Shift+Rechtsklick entfernt Korb bzw. letzten Linienpunkt, Linksklick öffnet das Menü erneut. `/bc arena save <name>` speichert die Halle. Körbe und Dreierlinien werden direkt gespeichert. `/bc showhoops` aktiviert sichtbare Partikelringe; `/bc three show` zeigt die Linien.

## Commands im Spiel
`/bc help`, `/bc help 2`, `/bc help 3`: vollständige Liste in Minecraft. Tab-Vervollständigung ist eingebaut.

## Fixes
- Sprint-Sperre bei leerer Ausdauer über Sprint-Event und verringerte Walk-Speed, Freigabe ab 25.
- Green-Zeitfenster 6/5/4/3 Ticks, kein GREEN-Titel oder Sound-Cue beim Aufladen.
- Bodenprüfung mit 0.25 Block visuellem Radius.
- 3-Punkte-Zone durch Strahl-Schnitt mit markierter Polygonlinie; Linie in Reihenfolge setzen, mindestens 3 Punkte. Wenn keine Linie markiert ist, greift der bisherige Radius-Fallback.

## Installation
Java 21, Paper 1.21.11. GitHub Actions -> Build BasketballCraft -> JAR aus Actions-Artefakten laden. Resourcepack aus dem separaten ZIP aktivieren (unverändertes Ballmodell).

## Grenzen
Die Dreierlinie ist eine offene, zusammenhängende Punktfolge; die Wertung verwendet den ersten Schnitt eines Strahls vom Korb zum Werfer. Die Linie sollte die relevante Spielfeldhälfte vollständig abdecken. Keine automatischen Einwürfe oder Teams. **Build und Ingame-Test sind noch nicht bestätigt.**
