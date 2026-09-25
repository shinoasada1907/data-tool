package com.universalimporter.domain.importsession;

import java.util.Optional;
import java.util.UUID;

public interface ImportSessionRepository {

    /** Stores the session and returns it as stored, with its new persistence version. */
    ImportSession save(ImportSession session);

    Optional<ImportSession> findById(UUID id);
}
