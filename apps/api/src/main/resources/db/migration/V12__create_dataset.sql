-- Toolbox datasets (core-04 PL5): one uploaded file each, kept at {storage}/{id}/source.bin. A dataset lives until
-- it has not been used for the retention period (24h by default), counted from last_used_at.
CREATE TABLE dataset (
    id                 UUID         PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    format             VARCHAR(8)   NOT NULL CHECK (format IN ('CSV', 'XLSX', 'JSON')),
    size_bytes         BIGINT       NOT NULL CHECK (size_bytes >= 0),
    sheets             JSONB,
    version            BIGINT       NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_used_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX dataset_last_used_at_idx ON dataset (last_used_at);
