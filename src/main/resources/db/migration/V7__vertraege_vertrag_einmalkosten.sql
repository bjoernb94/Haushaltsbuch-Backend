-- Einmalige Kosten eines Vertrags, z. B. Anschlussgebühr oder Hardware (#95); zählen nicht zu den laufenden
-- Kosten. Gehört zum Vertrag und ist nur über ihn erreichbar, daher ohne eigene haushalt_id. Gleiche Einträge
-- sind erlaubt (zwei Geräte am selben Tag).
CREATE TABLE vertrag_einmalkosten (
    vertrag_id      UUID           NOT NULL REFERENCES vertrag (id),
    bezeichnung     VARCHAR(200)   NOT NULL,
    faellig_am      DATE           NOT NULL,
    betrag_wert     NUMERIC(19, 2) NOT NULL,
    betrag_waehrung VARCHAR(3)     NOT NULL
);

CREATE INDEX vertrag_einmalkosten_vertrag_idx ON vertrag_einmalkosten (vertrag_id);
