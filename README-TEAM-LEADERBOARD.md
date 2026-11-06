# BasketballCraft – Team-Leaderboard

Zusätzlich zum bestehenden Spieler-Leaderboard gibt es eine separat platzierbare Team-Rangliste.

- `/bc teamboard set`: platziert die Team-Rangliste drei Blöcke vor dem Admin.
- `/bc teamboard remove`: entfernt nur die Team-Rangliste.
- `/bc teamboard refresh`: aktualisiert die Team-Rangliste.
- `/bc board set`: bisherige Spieler-Rangliste bleibt erhalten.
- `/bc leaderboard reset CONFIRM`: löscht nun Spieler- UND Team-Statistiken.

Sortierung: (1) Siege absteigend, (2) erzielte Teampunkte absteigend, (3) Teamname alphabetisch. Unentschieden geben keinem Team einen Sieg, erzielte Punkte werden trotzdem gezählt. Es werden nur registrierte Teams aus einem regulär beendeten Match mit ausgewähltem Matchup erfasst. Maximal acht Teams werden dargestellt. Die Daten liegen separat in `plugins/BasketballCraft/team-leaderboard.yml`.

Hinweis: Das Plugin muss noch für Paper 1.21.11 kompiliert und getestet werden. Die ZIP ist Quellcode, keine fertige .jar.
