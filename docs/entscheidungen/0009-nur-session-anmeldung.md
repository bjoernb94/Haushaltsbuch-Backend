# 0009 Nur Session-Anmeldung, keine Bearer-Tokens

**Status:** angenommen, 2026-11-01 ([#51](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/51)),
ergänzt [0002](0002-anmeldung-ueber-backend.md)

## Kontext

Neben der Session-Anmeldung aus [0002](0002-anmeldung-ueber-backend.md) nahm die Filterkette auch
Bearer-Tokens an (Spring Security Resource Server), gedacht für andere Clients und Tests. Einen
anderen Client gibt es nicht. Der Resource Server prüfte nur Issuer und Signatur, keine Audience:
Jedes Access Token des Realms galt, auch eines vom eingebauten öffentlichen Client `admin-cli`.
Ein solches Token umgeht die Session-Logik (Abmeldung, Prüfung der Keycloak-Sitzung) und gilt bis
zu seinem Ablauf.

## Entscheidung

Das Backend nimmt keine Bearer-Tokens an; angemeldet wird nur über `POST /api/anmeldung` bzw. die
Registrierung. Der Resource Server entfällt samt Abhängigkeit. Das ID-Token aus der Anmeldung
prüft `KeycloakTokenClient` mit einem eigenen Decoder: Signatur, Ablauf, Issuer und Audience
(unser Client).

## Folgen

- Ein Weg weniger, den Komponentenprüfung, Haushaltstrennung und Fehlerformat mitdenken müssen;
  auch 401 kommt jetzt immer als `ProblemDetail`.
- Tests melden sich mit `oidcLogin()` an, also so wie in prod (`DefaultOidcUser`), nicht mit `jwt()`.
- Braucht es später doch einen anderen Client (z.&nbsp;B. eine App), kommt der Resource Server mit
  Audience-Prüfung zurück, dann mit neuer Entscheidung.
