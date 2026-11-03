# BasketballCraft v0.8.5 — Ausdauer-Stop

Basis: v0.8.4. Keine Änderungen am Resourcepack, Court-Editor, Körben oder Dreierlinien.

- Bei 0 Ausdauer: horizontale Bewegung wird blockiert (Blickrichtung und vertikale Bewegung bleiben möglich).
- Solange der Spieler weiter versucht zu laufen, regeneriert die Ausdauer nicht.
- Erst nach Stillstand beginnt die Regeneration; bei 30 Ausdauer wird die Bewegung wieder freigegeben.
- Sprint bleibt während der Erschöpfung gesperrt.
- Im normalen Betrieb bleiben die bisherigen Stamina-Raten erhalten.

## Installation
ZIP ins GitHub-Repository hochladen und GitHub Actions ausführen. Die JAR aus dem Actions-Artefakt auf dem Paper-1.21.11-Server installieren. Kein neues Resourcepack nötig.

## Test
Sprinten bis 0 -> Weiterlaufen versuchen (keine Bewegung/Regeneration) -> Bewegungstasten loslassen (Regeneration) -> ab 30 wieder laufen.

**Hinweis:** Java-Build und Ingame-Test wurden hier nicht ausgeführt.
