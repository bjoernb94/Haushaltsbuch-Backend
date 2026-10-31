# 0003 Gebuchte Komponenten in der eigenen Datenbank

**Status:** angenommen, 2026-10-30; seit [#14](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/14) beim Haushalt statt beim Nutzer

## Kontext

Welche Fachbereiche jemand nutzen darf, hängt an den gebuchten Komponenten. Als Keycloak-Rollen
stünden sie im Access Token und würden erst nach einer neuen Anmeldung wirksam.

## Entscheidung

Die Komponenten liegen in unserer Datenbank (`haushalt_komponente`). `SecurityConfig` ordnet
jedem Fachmodul einen API-Pfad zu und prüft die Komponente bei jeder Anfrage über
`KomponentenAbfrage`. Keycloak-Rollen werden nicht ausgewertet.

## Folgen

- Änderungen an den Komponenten greifen sofort.
- Eine Datenbankabfrage je Anfrage auf einen Fachpfad.
- Endpunkte eines Fachmoduls müssen unter dessen Pfad liegen, sonst sind sie ungeschützt.
- Konten, die direkt in Keycloak angelegt werden, haben keine Komponenten und kommen in keinen
  Fachbereich.
