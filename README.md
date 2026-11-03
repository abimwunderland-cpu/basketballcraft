# BasketballCraft v0.3 – Shooting & Jerseys (prototype)

Paper 1.21.11 / Java 21 / Maven.

## Shooting
- Ball aufnehmen, **Linksklick** startet den wiederholenden Wurfmeter.
- **Nochmals Linksklick** löst den Wurf aus (Bukkit bietet kein zuverlässiges Loslassen der Maustaste über das normale Interact-Event).
- Grünes Fenster: Ticks 23–30 eines 44-Tick-Zyklus. Green Release verbessert die Konsistenz, ist kein Auto-Treffer.
- **Shift + Linksklick** passt sofort.
- Richtung und Blickwinkel bleiben wichtig.

## Jerseys
- `/bc jersey` gibt bordeauxfarbenes Trikot und Shorts.
- `BasketballCraft-Jerseys.zip` als Client-Resourcepack aktivieren.
- **Einschränkung:** Das Resourcepack überschreibt die Standard-Lederrüstung für ALLE Lederrüstungen. Es ist ein einfacher texturierter Armor-Prototyp, **kein echtes 3D-Modell** und noch nicht das exakte Frankreich-Design mit WOLFSFELL 10.
- Kopf/Skin bleiben erhalten. Für selektive Trikots und Rücken-Nummern brauchen wir später ein eigenes Equipment-Asset-System.

## Commands
`/bc ball`, `/bc sethoop red|blue`, `/bc showhoops`, `/bc jersey`, `/bc score`, `/bc reset`.

## Build
`mvn -B clean package` oder GitHub Actions (`.github/workflows/build.yml`). JAR unter `target/`.

**Teststatus:** Codeänderungen vorgenommen, nicht auf einem Paper-Server ingame-getestet.
