# BasketballCraft v0.6 (Paper 1.21.11)

## Installation
1. Java 21, Paper 1.21.11.
2. GitHub Actions Build (oder `mvn clean package`); JAR aus `target/` in `plugins/`.
3. `BasketballCraft-v0.6-Resourcepack.zip` in `.minecraft/resourcepacks`, aktivieren, F3+T.
4. Server komplett neu starten. Alte V0.5 JAR entfernen.
5. `/bc jersey` gibt Trikot/Shorts ins Inventar (manuell anziehen).
6. `/bc sethoop red` und `/bc sethoop blue` an den exakten Ringmittelpunkten setzen; `/bc showhoops` zeigt beide Trefferkreise.
7. `/bc ball`, Linksklick Laden, zweiter Linksklick Release. Auf Korb in horizontaler Richtung schauen.

## Änderungen
- Neues cleanes violettes Ballmodell (kein Gehirn-Zickzack mehr)
- Flugbahn wird aus Distanz, Gravitation und Luftwiderstand berechnet, auch bei Würfen vom Boden
- Korbtreffer anhand der tatsächlichen Durchquerung der Ringebene während Substeps
- Grüne Releases sehr genau; schlechte Releases können daneben gehen
- Jerseys werden nicht mehr zwangsweise angezogen
- Eigene Equipment-Texturen; das Sichtbarkeitsproblem muss auf einem 1.21.11 Client verifiziert werden

**Hinweis:** Technischer Quellcode-Release. Maven-/Ingame-Test muss noch durchgeführt werden. Kein Versprechen, dass das Equipment auf jedem Client sofort korrekt gerendert wird.
