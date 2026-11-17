# 0019 Auslieferung über nginx auf demselben Origin

**Status:** angenommen, 2026-11-02, nachgetragen mit [#127](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/127) (umgesetzt mit
[Frontend #25](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/25) und [Frontend #61](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/61))

## Kontext

Die SPA hält nur das Session-Cookie (ADR [0009](0009-nur-session-anmeldung.md)). Liegen SPA und Backend
auf verschiedenen Origins, braucht es CORS und Cookies mit `SameSite=None`. Außerdem soll das Image für
jeden Betreiber gleich sein, Impressum und Datenschutz brauchen aber dessen Angaben.

## Entscheidung

Das Image liefert die SPA mit nginx aus, ohne Root-Rechte.

- nginx leitet nur `/api`, `/actuator/health/systemstatus` und `/actuator/info` an das Backend weiter.
  So liegen SPA und API auf demselben Origin, und alle anderen Endpunkte des Backends bleiben unerreichbar.
- Strenge Content Security Policy (`script-src 'self'`). Deshalb ist `inlineCritical` im
  Production-Build aus, denn es schreibt ein Inline-Skript in `index.html`.
- Die Angaben des Betreibers kommen aus einer eingehängten `instanz.json`, nie aus dem Image oder dem
  Code.

## Folgen

- Kein CORS und keine Cookies mit `SameSite=None`; der CSRF-Schutz (Cookie `XSRF-TOKEN`) funktioniert ohne
  Sonderfälle.
- CSP und nginx-Regeln gelten nur im Image; `npm run image:pruefen` prüft sie dort.
- Ein neuer öffentlicher Actuator-Endpunkt für die SPA muss auch in die nginx-Konfiguration.
- Fehlt `instanz.json` oder ist sie unvollständig, zeigen Impressum und Datenschutz einen Hinweis statt
  der Angaben.
