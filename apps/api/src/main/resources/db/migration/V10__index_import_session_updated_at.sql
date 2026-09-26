-- BE-F11 D1: the cleanup looks up sessions by their last change, oldest first.
CREATE INDEX idx_import_session_updated_at ON import_session (updated_at);
