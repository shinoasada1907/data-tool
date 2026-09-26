-- Everything configured for a session (BE-F04 design S8). One row per session, created by the first PUT;
-- a session without a row has an empty configuration. Each later feature starts using its own JSON column.
CREATE TABLE import_configuration (
    session_id           UUID        PRIMARY KEY REFERENCES import_session (id) ON DELETE CASCADE,
    target_schema_json   JSONB       NOT NULL DEFAULT '{"fields": []}',
    mapping_json         JSONB       NOT NULL DEFAULT '{"mappings": []}',
    transformations_json JSONB       NOT NULL DEFAULT '{"transformations": []}',
    validations_json     JSONB       NOT NULL DEFAULT '{"validations": []}',
    version              BIGINT      NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
