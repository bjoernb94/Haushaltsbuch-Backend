# 0001 Modularer Monolith mit Spring Modulith

**Status:** angenommen, 2026-10-29 (Grundgerüst)

## Kontext

Das Haushaltsbuch hat mehrere fachlich getrennte Bereiche (Buchungen, Verträge, Versorgung,
Portfolio), die Nutzer einzeln buchen. Mehrere Dienste mit eigener Auslieferung und Datenbank
wären für diesen Umfang zu viel Aufwand, ein unstrukturierter Monolith wüchse aber schnell zusammen.

## Entscheidung

Ein Spring-Boot-Backend mit einer Datenbank, aufgeteilt in Module nach Fachbereichen
(Spring Modulith). Jedes direkte Unterpaket von `de.bochmann.haushaltsbuch` ist ein Modul; nur sein
Wurzelpaket ist öffentliche API. `shared` ist ein offenes Modul für gemeinsame Infrastruktur.
`ModularityTests` prüft die Grenzen bei jedem Build.

## Folgen

- Ein Deployment, eine Datenbank, Transaktionen über Module hinweg sind möglich, aber die
  Abhängigkeiten bleiben sichtbar und geprüft.
- Module sprechen über öffentliche Services oder Ereignisse miteinander; braucht `shared` Daten
  eines Moduls, definiert es eine Schnittstelle, die das Modul implementiert (z.&nbsp;B.
  `KomponentenAbfrage`, `HaushaltAbfrage`).
- Ein Modul ließe sich später herauslösen, wenn es nötig wird.
