# 0015 Release-Prozess

**Status:** angenommen, 2026-11-08, nachgetragen mit
[#122](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/122) (umgesetzt mit
[#80](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/80))

## Kontext

Backend und Frontend werden zusammen per Compose betrieben (`betrieb/compose.yaml`). Ein Release von Hand
kann eine Version veröffentlichen, die nicht zur `pom.xml` passt, mit offenen Issues erscheinen oder
Release Notes ohne Ordnung haben.

## Entscheidung

Ein Release entsteht nur aus einem Tag `v<version>` auf `main` (`.github/workflows/release.yml`).

- Der Tag muss zur Version in der `pom.xml` passen, und `betrieb/compose.yaml` muss Backend und Frontend in
  dieser Version starten.
- Es muss einen offenen Milestone mit genau dem Namen des Tags geben, ohne offene Issues; am Ende wird er
  geschlossen.
- Die Release Notes werden aus den PRs erzeugt und nach ihren Labels gruppiert (`.github/release.yml`).
- Backend und Frontend zählen gleich hoch; das Backend wird zuerst veröffentlicht.
- Vor 1.0 ist jedes Release eine Vorabversion, und das Image bekommt kein `latest`.

## Folgen

- Was in einem Release steckt, legt der Milestone fest; ein Issue, das nicht fertig wird, muss vorher
  verschoben werden.
- PRs brauchen ein Label, sonst landen sie in den Release Notes unter „Sonstiges“.
- Die Version wird per PR angehoben, getaggt wird erst nach dem Merge.
