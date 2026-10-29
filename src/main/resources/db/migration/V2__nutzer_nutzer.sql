-- Nutzer der Anwendung. Die ID ist die Benutzer-ID aus Keycloak (Claim "sub");
-- E-Mail, Name und Passwort liegen ausschließlich in Keycloak.
CREATE TABLE nutzer (
    id          UUID PRIMARY KEY,
    angelegt_am TIMESTAMPTZ NOT NULL
);

-- Bei der Registrierung gewählte Komponenten (Finanzübersicht, Vertragsübersicht, ...).
CREATE TABLE nutzer_komponente (
    nutzer_id  UUID        NOT NULL REFERENCES nutzer (id) ON DELETE CASCADE,
    komponente VARCHAR(40) NOT NULL,
    PRIMARY KEY (nutzer_id, komponente)
);
