## Worum geht es?

<!-- Kurz: was ändert sich und warum. -->

Closes #

## Checkliste

- [ ] Tests ergänzt bzw. angepasst, `./mvnw verify` grün (prüft die CI, auch ob `docs/openapi.json` und `docs/module` aktuell sind)
- [ ] Doku angepasst, wo nötig: API für die SPA (`docs/api.md`), Konventionen (`docs/entwicklung.md`), Betrieb (`docs/betrieb.md`), `CLAUDE.md`
- [ ] Neue Fehlercodes in der Tabelle in `docs/api.md`
- [ ] Architekturentscheidung? Dann neuer Eintrag unter `docs/entscheidungen/`
- [ ] Neue Entity oder Spalte? Neue Migration, bestehende Migrationen unverändert
- [ ] Neuer Fachendpunkt? Er liegt unter dem Modulpfad, und es gibt den Test „fremder Haushalt → 404“
- [ ] Vertragsfelder oder Fälle geändert? `scripts/testdaten.py` angepasst
- [ ] API inkompatibel für die SPA? Frontend-PR verlinkt
- [ ] Module oder Abhängigkeiten geändert? Mermaid-Diagramm im README angepasst
- [ ] Label und Milestone gesetzt
