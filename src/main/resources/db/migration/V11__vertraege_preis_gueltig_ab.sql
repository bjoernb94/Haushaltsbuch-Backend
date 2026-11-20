-- Jeder Preis hat ein Datum „gültig ab“, der früheste genau den Vertragsbeginn (#156). Bisher galt ein Preis ohne
-- Datum ab Vertragsbeginn, und vor dem frühesten Eintrag galt der erste rückwirkend; beides wird hier ausgeschrieben.

-- Ein Preis vor dem Beginn ist seit PreisNichtVorBeginn unmöglich und V9 hat ihn berücksichtigt. Gibt es ihn doch,
-- lieber abbrechen als still korrigieren.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM vertrag_preis p JOIN vertrag v ON v.id = p.vertrag_id WHERE p.gueltig_ab < v.beginn) THEN
        RAISE EXCEPTION 'vertrag_preis: Preis vor dem Vertragsbeginn, bitte vor der Migration von Hand bereinigen';
    END IF;
END $$;

-- 1. Ohne Datum und mit einem datierten Preis genau am Beginn: Der ohne Datum galt nur vor dem Beginn und entfällt.
DELETE FROM vertrag_preis p
USING vertrag v
WHERE v.id = p.vertrag_id
  AND p.gueltig_ab IS NULL
  AND EXISTS (SELECT 1 FROM vertrag_preis d WHERE d.vertrag_id = p.vertrag_id AND d.gueltig_ab = v.beginn);

-- 2. Die übrigen ohne Datum gelten ab Vertragsbeginn (beginn ist seit V9 Pflicht).
UPDATE vertrag_preis p
SET gueltig_ab = v.beginn
FROM vertrag v
WHERE v.id = p.vertrag_id
  AND p.gueltig_ab IS NULL;

-- 3. Beginnt der früheste Preis erst nach dem Beginn, galt er bis dahin rückwirkend: Er gilt jetzt ab Beginn.
UPDATE vertrag_preis p
SET gueltig_ab = v.beginn
FROM vertrag v
WHERE v.id = p.vertrag_id
  AND p.gueltig_ab = (SELECT min(e.gueltig_ab) FROM vertrag_preis e WHERE e.vertrag_id = p.vertrag_id)
  AND p.gueltig_ab > v.beginn;

-- 4. Ohne leere Werte braucht es kein NULLS NOT DISTINCT mehr.
ALTER TABLE vertrag_preis
    ALTER COLUMN gueltig_ab SET NOT NULL,
    DROP CONSTRAINT vertrag_preis_vertrag_id_gueltig_ab_key,
    ADD UNIQUE (vertrag_id, gueltig_ab);
