# 0008 Modultests binden `shared` ein

**Status:** angenommen, 2026-10-31 ([#44](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/44), PR [#48](https://github.com/bjoernb94/Haushaltsbuch-Backend/pull/48))

## Kontext

`@ApplicationModuleTest` entfernt alle Beans außerhalb des getesteten Moduls, auch die
Konfiguration aus `shared` (`FachdatenConfig` mit Auditing und Trennung der Haushalte, `Clock`).
Bei `FachdatenConfig` bleiben dabei die `@Bean`-Methoden stehen und verweisen auf die entfernte
Klasse. Modultests für Fachmodule waren so nicht möglich. Alternativen waren, Fachmodule nur mit
`@IntegrationTest` zu testen oder `shared` aufzuteilen.

## Entscheidung

`@ModuleTest` bindet `shared` ausdrücklich ein (`extraIncludes = "shared"`). Fachmodule nutzen
`@FachmodulTest`, das zusätzlich `HaushaltAbfrage` und `KomponentenAbfrage` (implementiert in
`haushalt`) durch Mocks ersetzt; der Test setzt den Haushalt mit `HaushaltKontext.fuer(...)`.

## Folgen

- Services und Repositories eines Fachmoduls lassen sich ohne den Rest der Anwendung testen.
- Jeder Modultest lädt die ganze Infrastruktur aus `shared`, auch Security; ein Kontext je Modul.
- Modultests dürfen nicht `@Transactional` sein, weil die Transaktion in `fuer(...)` beginnen muss.
