# 0004 Fachdaten gehören dem Haushalt

**Status:** angenommen, 2026-10-31 ([#14](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/14), PR [#21](https://github.com/bjoernb94/Haushaltsbuch-Backend/pull/21))

## Kontext

Ein Haushaltsbuch soll später von mehreren Personen gemeinsam geführt werden können (z.&nbsp;B.
Partner). Hängen Fachdaten an `nutzer_id`, müssten dafür später alle Tabellen migriert und die
Zugriffsprüfung neu gebaut werden.

## Entscheidung

Fachdaten und gebuchte Komponenten gehören einem Haushalt (eigenes Modul `haushalt`, getrennt
vom Konto in `nutzer`). Die Registrierung legt in derselben Transaktion einen Haushalt mit dem
Nutzer als einzigem Mitglied an. Fachtabellen speichern `haushalt_id`, nie `nutzer_id` als
Besitzer. Vorerst gehört ein Nutzer zu genau einem Haushalt.

## Folgen

- Mitglieder einladen ([#15](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/15)) braucht keine Migration der Fachdaten.
- Zugriffe auf Daten eines fremden Haushalts liefern 404, nicht 403, damit sich nicht erkennen
  lässt, ob eine ID existiert.
- Mehrere Haushalte je Nutzer bräuchten eine Auswahl des aktiven Haushalts.
