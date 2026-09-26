package com.universalimporter.support;

import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test double for the session repository; {@link #failOnSave()} simulates a database outage. */
public class InMemoryImportSessionRepository implements ImportSessionRepository {

    private final Map<UUID, ImportSession> sessions = new HashMap<>();
    private boolean failOnSave;
    private final java.util.Set<UUID> failDeleteFor = new java.util.HashSet<>();

    private Runnable beforeNextConditionalDelete = () -> { };

    /** Deleting these sessions fails, as when the database is down. */
    public void failDeleteFor(UUID... ids) {
        failDeleteFor.addAll(java.util.List.of(ids));
    }

    public void allowDeletes() {
        failDeleteFor.clear();
    }

    /** Runs {@code action} just before the next conditional delete, as a write racing the cleanup would. */
    public void beforeNextConditionalDelete(Runnable action) {
        this.beforeNextConditionalDelete = action;
    }

    public void failOnSave() {
        this.failOnSave = true;
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    @Override
    public ImportSession save(ImportSession session) {
        if (failOnSave) {
            throw new IllegalStateException("database is down");
        }
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<ImportSession> findById(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public java.util.List<UUID> findIdsUpdatedBefore(java.time.Instant cutoff, int limit) {
        return sessions.values().stream()
                .filter(session -> session.updatedAt().isBefore(cutoff))
                .sorted(java.util.Comparator.comparing(ImportSession::updatedAt))
                .limit(limit)
                .map(ImportSession::id)
                .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return sessions.containsKey(id);
    }

    @Override
    public boolean deleteIfNotUpdatedSince(UUID id, java.time.Instant cutoff) {
        Runnable race = beforeNextConditionalDelete;
        beforeNextConditionalDelete = () -> { };
        race.run();
        if (failDeleteFor.contains(id)) {
            throw new IllegalStateException("database is down");
        }
        ImportSession session = sessions.get(id);
        if (session == null || !session.updatedAt().isBefore(cutoff)) {
            return false;
        }
        sessions.remove(id);
        return true;
    }
}
