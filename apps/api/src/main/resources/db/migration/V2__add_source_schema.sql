-- What inspecting the uploaded file found (columns, row count, sheet); NULL until the file is inspected.
ALTER TABLE import_session ADD COLUMN source_schema JSONB;
