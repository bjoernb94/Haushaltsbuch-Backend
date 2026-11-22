-- Fällige Stufen der Preissteigerung legt das Backend beim Speichern als Preise an und kennzeichnet sie (#159,
-- ADR 0022). Bestehende Preise sind eingetragen; die Stufen bestehender Verträge entstehen beim nächsten Speichern,
-- bis dahin rechnet das Backend sie beim Lesen.
ALTER TABLE vertrag_preis
    ADD COLUMN automatisch BOOLEAN NOT NULL DEFAULT FALSE;
