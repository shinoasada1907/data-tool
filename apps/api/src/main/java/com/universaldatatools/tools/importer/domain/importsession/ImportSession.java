package com.universaldatatools.tools.importer.domain.importsession;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.SourceSchema;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One upload and everything configured for it. Status changes only through {@link #transitionTo}. */
public final class ImportSession {

    private final UUID id;
    private final SourceFile sourceFile;
    private final Instant createdAt;
    private final Long version;
    private SessionStatus status;
    private Instant updatedAt;
    /** What reading the file found; {@code null} until the session has been inspected. */
    private SourceSchema sourceSchema;

    private ImportSession(UUID id, SourceFile sourceFile, SessionStatus status,
                          Instant createdAt, Instant updatedAt, Long version, SourceSchema sourceSchema) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        this.sourceSchema = sourceSchema;
    }

    public static ImportSession create(UUID id, SourceFile sourceFile, Instant now) {
        return new ImportSession(id, sourceFile, SessionStatus.UPLOADED, now, now, null, null);
    }

    /** Rebuilds a stored session; {@code version} is the persistence version used for optimistic locking. */
    public static ImportSession restore(UUID id, SourceFile sourceFile, SessionStatus status,
                                        Instant createdAt, Instant updatedAt, Long version,
                                        SourceSchema sourceSchema) {
        return new ImportSession(id, sourceFile, status, createdAt, updatedAt, version, sourceSchema);
    }

    /** Records what reading the uploaded file found; allowed once, right after upload (design D2, D9). */
    public void markInspected(SourceSchema schema, Instant now) {
        if (status != SessionStatus.UPLOADED) {
            throw new DomainException(ErrorCode.SESSION_STATE_INVALID, "Source file has already been inspected.");
        }
        Objects.requireNonNull(schema, "schema");
        transitionTo(SessionStatus.CONFIGURING, now);
        this.sourceSchema = schema;
    }

    public Optional<SourceSchema> sourceSchema() {
        return Optional.ofNullable(sourceSchema);
    }

    public void transitionTo(SessionStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new DomainException(ErrorCode.SESSION_STATE_INVALID,
                    "Import session cannot move from " + status + " to " + target + ".");
        }
        status = target;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public SourceFile sourceFile() {
        return sourceFile;
    }

    public SessionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
