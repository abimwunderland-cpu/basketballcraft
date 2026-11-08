# BasketballCraft V3.1 – Streetball Editor und Feldgrenzen

## Setup
1. `/bc street create wolfsfell`
2. `/bc street wand` – erhält einen Blaze-Rod-Editor.
3. **Linksklick** mit dem Stab wechselt den Markierungsmodus: `hoop` → `spawn1` → `spawn2` → `three` → `bound1` → `bound2` → zurück zu `hoop`.
4. **Rechtsklick auf einen Block** speichert den jeweiligen Punkt. Für `hoop` auf den Block am Ring klicken, für `three` auf einen Block der gewünschten Dreiergrenze und für `bound1`/`bound2` auf zwei diagonal gegenüberliegende Ecken des gesamten Spielfelds.
5. `/bc street player 1 Name` und `/bc street player 2 Name`
6. `/bc street target 5`, `/bc street time 300`, `/bc street shotclock 20`
7. `/bc street start`

## Verhalten
- Spieler können während eines aktiven Streetball-Matches die markierten rechteckigen Feldgrenzen in X/Z nicht zu Fuß überschreiten; Sprünge sind möglich.
- Die Spawns müssen beim Start innerhalb der Feldgrenzen liegen.
- Die Dreiergrenze ist weiterhin eine **kreisförmige Distanz** um den Korb, die durch den `three`-Referenzpunkt festgelegt wird – **keine frei gezeichnete Linienkurve**.
- Der bestehende normale Teammodus und dessen Arena-Editor bleiben erhalten.

## Build
GitHub Actions `Build BasketballCraft` baut das Projekt mit Java 21 und Maven. Der Artefaktname lautet `BasketballCraft-V3-JAR`. Dieses ZIP ist **Quellcode, keine JAR**. Die Änderungen wurden in dieser Umgebung nicht gegen die Paper API kompiliert oder auf einem Server getestet.
