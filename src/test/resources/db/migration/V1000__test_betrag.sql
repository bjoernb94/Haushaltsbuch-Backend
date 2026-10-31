-- Nur in Tests (src/test/resources): Tabelle für BetragIntegrationTests. Hohe Versionsnummer,
-- damit sie hinter allen Migrationen aus src/main läuft.
CREATE TABLE test_betrag (
    id               UUID PRIMARY KEY,
    preis_wert       NUMERIC(19, 2) NOT NULL,
    preis_waehrung   VARCHAR(3)     NOT NULL,
    gebuehr_wert     NUMERIC(19, 2),
    gebuehr_waehrung VARCHAR(3)
);
