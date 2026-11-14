-- Preise eines Vertrags mit Gültigkeitsdatum (#93); der aktuelle Betrag wird beim Lesen berechnet, nicht
-- gespeichert. Gehört zum Vertrag und ist nur über ihn erreichbar, daher ohne eigene haushalt_id.
-- gueltig_ab ist nur beim ersten Eintrag leer (= ab Vertragsbeginn).
CREATE TABLE vertrag_preis (
    vertrag_id      UUID           NOT NULL REFERENCES vertrag (id),
    gueltig_ab      DATE,
    betrag_wert     NUMERIC(19, 2) NOT NULL,
    betrag_waehrung VARCHAR(3)     NOT NULL,
    UNIQUE NULLS NOT DISTINCT (vertrag_id, gueltig_ab)
);

-- Bestehende Verträge: ihr Betrag gilt ab Vertragsbeginn (ohne Beginn leer).
INSERT INTO vertrag_preis (vertrag_id, gueltig_ab, betrag_wert, betrag_waehrung)
SELECT id, beginn, betrag_wert, betrag_waehrung FROM vertrag;

ALTER TABLE vertrag DROP COLUMN betrag_wert, DROP COLUMN betrag_waehrung;
