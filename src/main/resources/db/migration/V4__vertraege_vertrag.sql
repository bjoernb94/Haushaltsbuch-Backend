-- Vertragskategorien: Standardkategorien (haushalt_id NULL, schluessel übersetzt die SPA) und ab #34
-- eigene Kategorien eines Haushalts (name). Gemischte Zeilen, daher kein @TenantId; gefiltert wird im Code.
CREATE TABLE vertrag_kategorie (
    id          UUID PRIMARY KEY,
    haushalt_id UUID,
    schluessel  VARCHAR(40) UNIQUE,
    name        VARCHAR(100),
    reihenfolge INT         NOT NULL,
    CHECK ((schluessel IS NULL) <> (name IS NULL))
);

CREATE INDEX vertrag_kategorie_haushalt_idx ON vertrag_kategorie (haushalt_id);

-- Feste IDs, damit die Standardkategorien in allen Umgebungen gleich sind.
INSERT INTO vertrag_kategorie (id, schluessel, reihenfolge) VALUES
    ('615b0c17-281e-457f-b7ce-1d0d8ed7c12c', 'STREAMING_ABOS', 10),
    ('0fdf031f-b3a7-4c2f-ad12-b76bf1febcf9', 'SOFTWARE_APPS', 20),
    ('7cb7a2b8-38a6-432d-8546-03b3d74f76ec', 'VERSICHERUNG', 30),
    ('8fe4039e-2d9e-45e4-b94b-555d1e193fc9', 'MOBILFUNK_INTERNET', 40),
    ('e1c9451c-19c8-481c-b4ca-39bc389cafa1', 'MITGLIEDSCHAFTEN', 50),
    ('759afb04-8473-4352-ad7f-387afff690c8', 'GEBUEHREN_BEITRAEGE', 60),
    ('df419f96-28b5-4851-88c2-4634032072e6', 'SONSTIGES', 70);

-- Verträge eines Haushalts. Gespeichert werden nur Angaben des Nutzers; Status und nächste Abbuchung
-- berechnet vertraege.berechnung (abbuchung_ab: ab diesem Tag im Rhythmus der Zahlweise).
CREATE TABLE vertrag (
    id                      UUID PRIMARY KEY,
    haushalt_id             UUID           NOT NULL,
    version                 BIGINT         NOT NULL,
    angelegt_am             TIMESTAMPTZ    NOT NULL,
    angelegt_von            UUID,
    geaendert_am            TIMESTAMPTZ    NOT NULL,
    geaendert_von           UUID,
    anbieter                VARCHAR(200)   NOT NULL,
    bezeichnung             VARCHAR(200),
    kategorie_id            UUID           NOT NULL REFERENCES vertrag_kategorie (id),
    vertragsnummer          VARCHAR(100),
    betrag_wert             NUMERIC(19, 2) NOT NULL,
    betrag_waehrung         VARCHAR(3)     NOT NULL,
    zahlweise               VARCHAR(40)    NOT NULL,
    abbuchung_ab            DATE           NOT NULL,
    zahlungsweg             VARCHAR(40),
    kuendbar                BOOLEAN        NOT NULL,
    beginn                  DATE,
    laufzeitende            DATE,
    verlaengerung           VARCHAR(40),
    kuendigungsfrist_monate SMALLINT,
    gekuendigt_zum          DATE,
    notiz                   VARCHAR(1000)
);

CREATE INDEX vertrag_haushalt_idx ON vertrag (haushalt_id);
CREATE INDEX vertrag_kategorie_idx ON vertrag (kategorie_id);
