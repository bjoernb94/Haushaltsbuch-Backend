# 0006 Trennung der Haushalte per Hibernate-`@TenantId`

**Status:** angenommen, 2026-10-31 ([#26](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/26), PR [#45](https://github.com/bjoernb94/Haushaltsbuch-Backend/pull/45))

## Kontext

Bis dahin musste jede Repository-Methode selbst auf `haushalt_id` filtern. Fehlt der Filter
einmal, sieht ein Nutzer Daten eines fremden Haushalts, ohne dass ein Test anschlägt. Geprüft
wurden: Hibernate-`@TenantId`, weiter von Hand filtern mit Pflicht-Test je Modul, und Row Level
Security in PostgreSQL (für den Umfang zu aufwendig).

## Entscheidung

`FachEntity.haushaltId` ist eine Hibernate-`@TenantId`. `HaushaltTenantResolver` liefert beim
Öffnen jeder Session den Haushalt aus `HaushaltKontext`: im Request den des angemeldeten Nutzers
(einmal je Request per JDBC ermittelt), ohne Request ausdrücklich über
`HaushaltKontext.fuer(...)`, sonst den Platzhalter `KEIN_HAUSHALT`.

## Folgen

- Hibernate filtert jede Abfrage auf Fach-Entities und setzt `haushalt_id` beim Anlegen;
  Fachmodule können den Filter nicht vergessen.
- Native SQL und Tabellen mit gemischten Zeilen (Standardwerte mit `haushalt_id IS NULL`) filtert
  Hibernate nicht; dort filtert der Code selbst.
- Der Haushalt wird beim Öffnen der Session gelesen: Transaktionen ohne Request müssen innerhalb
  von `fuer(...)` beginnen. Für Listener auf Ereignisse braucht es dafür einen Baustein
  ([#46](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/46)).
- Der Pflicht-Test „fremder Haushalt → 404“ je Fachmodul bleibt als zweite Absicherung.
