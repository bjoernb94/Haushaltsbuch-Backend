-- Regelmäßige Preissteigerung, Preisgarantie und Erinnerung ans Sonderkündigungsrecht (#96). Die Prognose wird
-- daraus beim Lesen berechnet, nicht gespeichert. preissteigerung_* sind alle leer ohne Regel; prozent nur bei
-- art PROZENT, betrag_* nur bei BETRAG.
ALTER TABLE vertrag
    ADD COLUMN preissteigerung_art              VARCHAR(40),
    ADD COLUMN preissteigerung_prozent          NUMERIC(5, 2),
    ADD COLUMN preissteigerung_betrag_wert      NUMERIC(19, 2),
    ADD COLUMN preissteigerung_betrag_waehrung  VARCHAR(3),
    ADD COLUMN preissteigerung_rhythmus         VARCHAR(40),
    ADD COLUMN preissteigerung_naechste_am      DATE,
    ADD COLUMN preisgarantie_bis                DATE,
    -- Bestehende Verträge: an, wie der Standard im Formular.
    ADD COLUMN erinnerung_sonderkuendigung      BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE vertrag ALTER COLUMN erinnerung_sonderkuendigung DROP DEFAULT;
