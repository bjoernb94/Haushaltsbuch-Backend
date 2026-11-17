# 0018 Services halten keine Fachdaten

**Status:** angenommen, 2026-11-02, nachgetragen mit [#127](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/127)

## Kontext

Ein Service in Angular lebt so lange wie die Anwendung. Hält er Fachdaten, etwa die Liste der Verträge,
bleiben sie nach dem Abmelden im Speicher, und meldet sich im selben Tab ein anderes Mitglied an, sieht es
für einen Moment die Daten des vorigen. Außerdem veraltet ein Zwischenspeicher, sobald ein anderes Mitglied
des Haushalts etwas ändert.

## Entscheidung

Services in `features/` laden und schreiben nur, sie halten keine Fachdaten. Die Daten liegen in der Seite:
Sie lädt beim Öffnen und nach einer Änderung neu.

Ausgenommen sind die Sitzungsdaten im `AuthService` (angemeldeter Nutzer, gebuchte Komponenten) und der
Systemstatus. Sie werden beim Abmelden zurückgesetzt bzw. betreffen keinen Nutzer.

## Folgen

- Nach dem Abmelden bleibt nichts für den nächsten Nutzer im Speicher, und jede Seite zeigt den aktuellen
  Stand.
- Mehr Anfragen, etwa beim Wechsel zwischen Seiten; bei den Datenmengen eines Haushalts unkritisch.
- Jede Seite mit Daten braucht einen Lade-, einen Fehler- und einen Leerzustand.
- Teilen sich Seiten Daten, lädt die übergeordnete Route sie und reicht sie weiter (z.&nbsp;B. Vertragsliste
  an Details und Bearbeiten), statt sie in einen Service zu legen.
