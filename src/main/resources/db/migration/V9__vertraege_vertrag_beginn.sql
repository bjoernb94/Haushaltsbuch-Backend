-- Der Vertragsbeginn ist Pflicht und Anker des Zahlungsrhythmus, abbuchung_ab entfällt (#110).
--
-- Verträge ohne Beginn bekommen ein ERFUNDENES Datum, keinen echten Vertragsbeginn (bei Einführung gab es nur lokale
-- Testdaten): abbuchung_ab, im Takt der Zahlweise so weit zurückgezählt, dass es spätestens am 01.01. des laufenden
-- Jahres liegt (sonst bräche „Kosten im Jahr“ ein, ohne Beginn zählte es ab 01.01.) und nicht nach dem ersten
-- gueltig_ab, dem Laufzeitende oder gekuendigt_zum (sonst verletzte es PreisNichtVorBeginn, LaufzeitendeNachBeginn
-- oder wäre storniert). Gezählt wird vom ursprünglichen Anker aus, damit der 31. nicht auf den 28. rutscht.
UPDATE vertrag v
SET beginn = (
    SELECT (v.abbuchung_ab - make_interval(months => k * m.monate))::date
    FROM (SELECT CASE v.zahlweise
                     WHEN 'MONATLICH' THEN 1
                     WHEN 'VIERTELJAEHRLICH' THEN 3
                     WHEN 'HALBJAEHRLICH' THEN 6
                     ELSE 12
                 END AS monate) m,
         generate_series(0, 2400) k
    WHERE (v.abbuchung_ab - make_interval(months => k * m.monate))::date <= LEAST(
            date_trunc('year', CURRENT_DATE)::date,
            (SELECT min(p.gueltig_ab) FROM vertrag_preis p WHERE p.vertrag_id = v.id),
            v.laufzeitende,
            v.gekuendigt_zum)
    ORDER BY k
    LIMIT 1)
WHERE v.beginn IS NULL;

ALTER TABLE vertrag
    ALTER COLUMN beginn SET NOT NULL,
    DROP COLUMN abbuchung_ab;
