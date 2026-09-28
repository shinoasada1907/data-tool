package com.universaldatatools.tools.importer.domain.importsession;

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

    /**
     * Deletes the session and its configuration, only if it has not changed since {@code cutoff}: the check and
     * the delete are one step, so a write that lands meanwhile keeps the session.
     *
     * @return whether it was deleted
     */
    boolean deleteIfNotUpdatedSince(UUID id, Instant cutoff);
}
