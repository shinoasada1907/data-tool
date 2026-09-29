-- Toolbox runs (core-06, core-04 PL6): the stored result of one Validator, Cleaner or Diff execution. Its sections
-- are NDJSON files in {storage}/{id}/, written to a staging directory first and renamed into place before this row
-- is inserted, so a row never points at missing files. A run lives until it has not been used for the retention
-- period (24h by default), counted from last_used_at.
CREATE TABLE tool_run (
    id           UUID        PRIMARY KEY,
    tool         VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    sources      JSONB       NOT NULL,
    config       JSONB       NOT NULL,
    summary      JSONB       NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX tool_run_last_used_at_idx ON tool_run (last_used_at);
