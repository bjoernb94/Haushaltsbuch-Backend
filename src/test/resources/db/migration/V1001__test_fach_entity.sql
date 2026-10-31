-- Nur in Tests: Tabelle für FachEntityTests, mit den Spalten, die jede Fach-Entity braucht.
CREATE TABLE test_fach_entity (
    id            UUID PRIMARY KEY,
    haushalt_id   UUID         NOT NULL,
    version       BIGINT       NOT NULL,
    angelegt_am   TIMESTAMPTZ  NOT NULL,
    angelegt_von  UUID,
    geaendert_am  TIMESTAMPTZ  NOT NULL,
    geaendert_von UUID,
    text          VARCHAR(100) NOT NULL
);
