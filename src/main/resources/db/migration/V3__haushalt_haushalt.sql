-- Haushalt als Besitzer der Fachdaten. Fachtabellen speichern haushalt_id statt nutzer_id,
-- damit mehrere Nutzer ein Haushaltsbuch gemeinsam führen können.
CREATE TABLE haushalt (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100),
    angelegt_am TIMESTAMPTZ NOT NULL
);

-- Mitglieder des Haushalts. Vorerst gehört ein Nutzer zu genau einem Haushalt (UNIQUE).
-- Bewusst kein Fremdschlüssel auf nutzer: keine Fremdschlüssel über Modulgrenzen.
CREATE TABLE haushalt_mitglied (
    haushalt_id UUID NOT NULL REFERENCES haushalt (id) ON DELETE CASCADE,
    nutzer_id   UUID NOT NULL UNIQUE,
    PRIMARY KEY (haushalt_id, nutzer_id)
);

-- Gebuchte Komponenten gelten für den ganzen Haushalt.
CREATE TABLE haushalt_komponente (
    haushalt_id UUID        NOT NULL REFERENCES haushalt (id) ON DELETE CASCADE,
    komponente  VARCHAR(40) NOT NULL,
    PRIMARY KEY (haushalt_id, komponente)
);

-- Bestehende Nutzer: je Nutzer ein eigener Haushalt mit seinen bisherigen Komponenten.
CREATE TEMPORARY TABLE zuordnung AS
SELECT id AS nutzer_id, gen_random_uuid() AS haushalt_id, angelegt_am
FROM nutzer;

INSERT INTO haushalt (id, angelegt_am)
SELECT haushalt_id, angelegt_am FROM zuordnung;

INSERT INTO haushalt_mitglied (haushalt_id, nutzer_id)
SELECT haushalt_id, nutzer_id FROM zuordnung;

INSERT INTO haushalt_komponente (haushalt_id, komponente)
SELECT z.haushalt_id, nk.komponente
FROM nutzer_komponente nk
JOIN zuordnung z ON z.nutzer_id = nk.nutzer_id;

DROP TABLE zuordnung;
DROP TABLE nutzer_komponente;
