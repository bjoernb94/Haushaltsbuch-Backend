# 0021 Dokumentation im Repo, kein Wiki

**Status:** angenommen, 2026-11-18, mit
[#149](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/149) (geprüft in
[Frontend #138](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/138))

## Kontext

Seit die Repos öffentlich sind, steht ihnen das GitHub-Wiki zur Verfügung. Zu klären war, ob die
Dokumentation beider Repos dorthin umzieht.

## Entscheidung

Die Dokumentation bleibt in den Repos (README, `docs/`), das Wiki ist in beiden Repos ausgeschaltet.

- Die Doku gehört zum Stand des Codes und wird im selben PR geändert und geprüft. Das Wiki ist ein eigenes
  Git-Repo ohne PRs und Review; Änderungen gehen sofort live.
- Ein Teil wird von Tests erzeugt und in der CI geprüft (`docs/openapi.json`, `docs/module`). Im Wiki ginge
  das nur über einen eigenen Workflow mit Token.
- Das Wiki kennt nur einen Stand, die Doku im Repo passt zu jedem Release-Tag.
- Code, Workflows und das Frontend (`npm run api:typen`) verweisen auf Dateien unter `docs/`, und
  `CLAUDE.md` muss ohnehin im Repo liegen.

## Folgen

- Doku wird im PR mit dem Code angepasst, nicht nachträglich.
- Auch eine spätere Bedienungsanleitung für Nutzer liegt im Repo. Braucht sie eine eigene Seite, ist
  GitHub Pages aus `docs/` (versioniert, in der CI gebaut) die bessere Wahl als das Wiki.
