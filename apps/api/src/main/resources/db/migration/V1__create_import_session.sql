CREATE TABLE import_session (
    id                 UUID         PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    file_type          VARCHAR(10)  NOT NULL CHECK (file_type IN ('CSV', 'XLSX')),
    size_bytes         BIGINT       NOT NULL CHECK (size_bytes >= 0),
    status             VARCHAR(20)  NOT NULL
                       CHECK (status IN ('UPLOADED', 'CONFIGURING', 'READY', 'PROCESSED', 'FAILED')),
    version            BIGINT       NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);
