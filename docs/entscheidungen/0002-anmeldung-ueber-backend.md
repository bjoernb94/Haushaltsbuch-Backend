# 0002 Anmeldung über das Backend mit Password Grant

**Status:** angenommen, 2026-10-30

## Kontext

Die SPA soll ein eigenes Login- und Registrierungsformular im eigenen Design haben, ohne
Weiterleitung auf die Seiten von Keycloak. Tokens sollen nicht im Browser liegen.

## Entscheidung

Die SPA schickt E-Mail und Passwort an `POST /api/anmeldung`. Das Backend holt die Tokens per
Password Grant (*Direct access grants*) bei Keycloak, legt eine HTTP-Session an und speichert dort
das Refresh Token; die SPA erhält nur das Session-Cookie (Backend for Frontend). Kein
`oauth2Login`, keine Weiterleitung. Konten legt das Backend über die Keycloak Admin API an.

## Folgen

- Das Backend sieht Passwörter; Verbindungen zu Keycloak müssen in prod über HTTPS laufen.
- Schutz gegen Durchprobieren über die Brute-Force-Erkennung von Keycloak, später zusätzlich je
  IP-Adresse ([#6](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/6)).
- Session-Cookie braucht CSRF-Schutz (`XSRF-TOKEN`), und die SPA muss das Backend über denselben
  Origin erreichen.
- Der Password Grant ist in OAuth 2.1 nicht mehr vorgesehen; Funktionen, die Keycloak nur über
  seine Seiten anbietet (Pflichtschritte, „Passwort vergessen“), muss das Backend selbst abbilden
  ([#1](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/1)).
- Sitzungen hängen am Speicher des Backends, bis Spring Session kommt ([#5](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/5)).
