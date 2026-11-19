-- Komponenten lassen sich nachträglich ändern (#3): Version gegen stilles Überschreiben (ADR 0012) und
-- Audit-Spalten wie bei Fach-Entities, damit nachvollziehbar ist, wer wann geändert hat.
ALTER TABLE haushalt
    ADD COLUMN version       BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN angelegt_von  UUID,
    ADD COLUMN geaendert_am  TIMESTAMPTZ,
    ADD COLUMN geaendert_von UUID;

-- Bestehende Haushalte haben genau ein Mitglied, den Gründer; seitdem wurden sie nicht geändert.
UPDATE haushalt h
SET angelegt_von = (SELECT m.nutzer_id FROM haushalt_mitglied m WHERE m.haushalt_id = h.id LIMIT 1),
    geaendert_am = h.angelegt_am;

ALTER TABLE haushalt
    ALTER COLUMN geaendert_am SET NOT NULL;
