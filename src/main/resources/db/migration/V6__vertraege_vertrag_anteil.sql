-- Eigener Anteil an geteilten Verträgen in Prozent (#94): 1–100 mit einer Nachkommastelle, leer = 100 %.
-- Übersichten zählen nur diesen Anteil der Kosten.
ALTER TABLE vertrag ADD COLUMN anteil_prozent NUMERIC(4, 1);
