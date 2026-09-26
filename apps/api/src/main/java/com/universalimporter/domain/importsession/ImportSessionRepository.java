package com.universalimporter.domain.importsession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportSessionRepository {

    /** Stores the session and returns it as stored, with its new persistence version. */
    ImportSession save(ImportSession session);

    Optional<ImportSession> findById(UUID id);

    /** Ids of the sessions last changed before {@code cutoff}, oldest change first, at most {@code limit}. */
    List<UUID> findIdsUpdatedBefore(Instant cutoff, int limit);

    boolean existsById(UUID id);

    /** Deletes the session and its configuration; does nothing when there is none. */
    void deleteById(UUID id);
}
