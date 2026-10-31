# 0005 Keine Fremdschlüssel über Modulgrenzen

**Status:** angenommen, 2026-10-31 ([#14](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/14))

## Kontext

Alle Module liegen in einer Datenbank. Fremdschlüssel zwischen ihren Tabellen würden die Module
auf Datenbankebene koppeln, die Spring Modulith im Code getrennt hält.

## Entscheidung

Tabellen verweisen auf Daten anderer Module nur über die ID (z.&nbsp;B. `haushalt_id`,
`haushalt_mitglied.nutzer_id`), ohne Fremdschlüssel. Innerhalb eines Moduls sind Fremdschlüssel
erwünscht.

## Folgen

- Module lassen sich unabhängig ändern und notfalls herauslösen.
- Die Datenbank sorgt nicht für Konsistenz über Module hinweg: Löscht ein Modul Daten, müssen die
  anderen per Ereignis nachziehen (Konto löschen, [#16](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/16); Haushaltskontext für
  Listener, [#46](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/46)).
