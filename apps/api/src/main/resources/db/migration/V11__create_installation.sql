-- BE-F11 review: one lasting id per database. The storage folder carries the id of the database it belongs to,
-- so a cleanup connected to another database never takes that folder's directories for orphans.
CREATE TABLE installation (
    id UUID PRIMARY KEY
);
INSERT INTO installation (id) VALUES (gen_random_uuid());
