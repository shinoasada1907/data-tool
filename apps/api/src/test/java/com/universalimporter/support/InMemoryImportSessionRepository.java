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
}
