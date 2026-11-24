-- Unterbrechungen eines Vertrags (#166, ADR 0023): die Zeit zwischen einer Kündigung und der Wiederaufnahme, in der
-- nichts abgebucht wird. Gespeichert wird nur die Angabe des Nutzers; Phasen, Status und Abbuchungen berechnet
-- vertraege.berechnung. Gehört zum Vertrag und ist nur über ihn erreichbar, daher ohne eigene haushalt_id; die id
-- braucht das Entfernen über PUT (unterbrechungIds). art: KUENDIGUNG, später auch geplante Pausen.
CREATE TABLE vertrag_unterbrechung (
    id         UUID PRIMARY KEY,
    vertrag_id UUID        NOT NULL REFERENCES vertrag (id),
    von        DATE        NOT NULL,
    bis        DATE        NOT NULL,
    art        VARCHAR(40) NOT NULL
);

CREATE INDEX vertrag_unterbrechung_vertrag_idx ON vertrag_unterbrechung (vertrag_id);
