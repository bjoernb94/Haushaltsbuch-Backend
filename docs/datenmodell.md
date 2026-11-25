# Datenmodell

Erzeugt von `DatenmodellExportTests` aus dem Schema der Flyway-Migrationen
(`src/main/resources/db/migration`), nicht von Hand ändern. Ändert sich das Schema, die Datei mit
`./mvnw test -Dtest=DatenmodellExportTests` neu erzeugen und mit committen; die CI prüft, ob sie
aktuell ist. Konventionen für das Schema stehen unter [Entwicklung](entwicklung.md#datenbank).

- **Durchgezogene Linie:** Fremdschlüssel, nur innerhalb eines Moduls.
- **Gestrichelte Linie:** Verweis über eine Modulgrenze ohne Fremdschlüssel
  ([ADR 0005](entscheidungen/0005-keine-fremdschluessel-ueber-module.md)), erkannt an einer Spalte
  `<tabelle>_id`. Audit-Spalten wie `angelegt_von` verweisen auch auf einen Nutzer, haben aber keine Linie.
- `PK`, `FK`, `UK`: Primärschlüssel, Fremdschlüssel, eindeutig (nur einspaltig). `NULL`: Spalte darf leer sein.

```mermaid
erDiagram
    haushalt {
        uuid id PK
        varchar(100) name "NULL"
        timestamptz angelegt_am
        bigint version
        uuid angelegt_von "NULL"
        timestamptz geaendert_am
        uuid geaendert_von "NULL"
    }
    haushalt_komponente {
        uuid haushalt_id PK, FK
        varchar(40) komponente PK
    }
    haushalt_mitglied {
        uuid haushalt_id PK, FK
        uuid nutzer_id PK, UK
    }
    nutzer {
        uuid id PK
        timestamptz angelegt_am
    }
    vertrag {
        uuid id PK
        uuid haushalt_id
        bigint version
        timestamptz angelegt_am
        uuid angelegt_von "NULL"
        timestamptz geaendert_am
        uuid geaendert_von "NULL"
        varchar(200) anbieter
        varchar(200) bezeichnung "NULL"
        uuid kategorie_id FK
        varchar(100) vertragsnummer "NULL"
        varchar(40) zahlweise
        varchar(40) zahlungsweg "NULL"
        boolean kuendbar
        date beginn
        date laufzeitende "NULL"
        varchar(40) verlaengerung "NULL"
        smallint kuendigungsfrist_monate "NULL"
        date gekuendigt_zum "NULL"
        varchar(1000) notiz "NULL"
        numeric anteil_prozent "(4,1), NULL"
        varchar(40) preissteigerung_art "NULL"
        numeric preissteigerung_prozent "(5,2), NULL"
        numeric preissteigerung_betrag_wert "(19,2), NULL"
        varchar(3) preissteigerung_betrag_waehrung "NULL"
        varchar(40) preissteigerung_rhythmus "NULL"
        date preissteigerung_naechste_am "NULL"
        date preisgarantie_bis "NULL"
        boolean erinnerung_sonderkuendigung
        date preissteigerung_betrachtet_bis "NULL"
    }
    vertrag_einmalkosten {
        uuid vertrag_id FK
        varchar(200) bezeichnung
        date faellig_am
        numeric betrag_wert "(19,2)"
        varchar(3) betrag_waehrung
    }
    vertrag_kategorie {
        uuid id PK
        uuid haushalt_id "NULL"
        varchar(40) schluessel UK "NULL"
        varchar(100) name "NULL"
        integer reihenfolge
    }
    vertrag_preis {
        uuid vertrag_id FK
        date gueltig_ab
        numeric betrag_wert "(19,2)"
        varchar(3) betrag_waehrung
        boolean automatisch
    }
    vertrag_unterbrechung {
        uuid id PK
        uuid vertrag_id FK
        date von
        date bis
        varchar(40) art
    }
    haushalt ||--o{ haushalt_komponente : "haushalt_id"
    haushalt ||--o{ haushalt_mitglied : "haushalt_id"
    haushalt ||..o{ vertrag : "haushalt_id"
    haushalt |o..o{ vertrag_kategorie : "haushalt_id"
    nutzer ||..o| haushalt_mitglied : "nutzer_id"
    vertrag ||--o{ vertrag_einmalkosten : "vertrag_id"
    vertrag ||--o{ vertrag_preis : "vertrag_id"
    vertrag ||--o{ vertrag_unterbrechung : "vertrag_id"
    vertrag_kategorie ||--o{ vertrag : "kategorie_id"
```

## Tabellen je Modul

| Modul | Tabellen |
|-------|----------|
| `haushalt` | `haushalt`, `haushalt_komponente`, `haushalt_mitglied` |
| `nutzer` | `nutzer` |
| `vertraege` | `vertrag`, `vertrag_einmalkosten`, `vertrag_kategorie`, `vertrag_preis`, `vertrag_unterbrechung` |
