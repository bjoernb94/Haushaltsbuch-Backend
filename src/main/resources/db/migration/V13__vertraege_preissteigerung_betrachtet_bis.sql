-- Bis zu diesem Tag hat das Speichern die Termine der Preissteigerung betrachtet und fällige Stufen angelegt; ein
-- Termin bis dahin entsteht nie wieder, auch wenn der Nutzer seinen Preis gelöscht hat (#162). Leer = noch nie mit
-- Regel gespeichert; das stimmt für alle Verträge aus 0.2.1 (V12 lief nur auf Entwicklungsdatenbanken).
ALTER TABLE vertrag
    ADD COLUMN preissteigerung_betrachtet_bis DATE;
