# BasketballCraft V1.6 – Entwicklungsstand

**WICHTIG: Nicht als getestete Finalversion ausgeben.** Der Quellcode wurde in dieser Umgebung nicht gegen die Paper-API kompiliert und nicht im Spiel getestet.

Enthalten im Source:
- 10 Sekunden Match-Countdown und Ball-Aufnahmesperre
- Court-Grenze für aktive Spieler, verbesserte Passsegment-Kollision
- Teamweste geschützt, Shift bricht Wurf ab
- Inkrementelle Scoreboard-Updates statt kompletter Löschung
- Zwei frei benennbare Matchseiten mit einmalig vergebener Teamfarbe, `/bc league set 1 lila Bierathleten` und `/bc league set 2 orange Dunking Ducks`
- `/bc music on|off`, Matchmusik-Start/-Stopp, individuelle Punktsounds

Enthalten im Resourcepack:
- Lila 3D-Basketball
- Eigenständig synthetisierter moderner instrumentaler NBA-artiger Beat ohne fremde Musik-Samples
- Individuelle OGG-Sounds für 2, 3, 4 Punkte und Dunk

Noch offen / nicht fertig:
- Beliebig viele registrierte Teams und Teamverwaltung über zwei aktive Seiten hinaus
- Coach-NPCs mit individuellen Skins und Menüs
- Physikalische Ballbegrenzung, Ballrotation und vollständige Tests
- Reproduzierbare Kompilierung / JAR

Die Quelle ist für Paper 1.21.11 und Java 21 konzipiert. GitHub Actions kann mit dem vorhandenen Workflow eine JAR bauen, sofern der Code kompiliert.
