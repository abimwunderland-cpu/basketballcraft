# BasketballCraft: echtes Licht am Neon-Feldrand

`/bc neon on` erzeugt zusätzlich zu violetten Partikeln unsichtbare Vanilla-LIGHT-Blöcke (Lichtstärke 15) entlang der Feldgrenze im Abstand von ca. 3 Blöcken. Das beleuchtet tatsächlich umliegende Blöcke. Minecraft-Licht selbst ist nicht lila; nur die Partikel sind violett.

Nur AIR/CAVE_AIR wird ersetzt, keine Baublöcke. Bei `/bc neon off`, Server-Stopp, Arena-Wechsel und neuer Bounds-Markierung werden die gesetzten LIGHT-Blöcke entfernt bzw. neu aufgebaut. Die Positionen werden in config.yml festgehalten.

Wichtig: Für die Lichtblöcke muss die Feldgrenze auf der Höhe freier Luft direkt über dem Boden liegen. Wenn die Grenzposition durch eine Blockfläche verläuft, wird dort nichts ersetzt.

Status: Quellcode-Patch, nicht gegen Paper 1.21.11 kompiliert oder im Spiel getestet.
