package com.universalimporter.api.importsession;

import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFileType;

import java.time.Instant;
import java.util.UUID;

/** {@code ImportSessionDto} of the API contract V0.1 (be-f01 design.md). */
public record ImportSessionDto(
        UUID id,
        SessionStatus status,
        String originalFileName,
        SourceFileType fileType,
        long sizeBytes,
        Instant createdAt,
        Instant updatedAt) {

    public static ImportSessionDto from(ImportSession session) {
        return new ImportSessionDto(
                session.id(),
                session.status(),
                session.sourceFile().originalFileName(),
                session.sourceFile().fileType(),
                session.sourceFile().sizeBytes(),
                session.createdAt(),
                session.updatedAt());
    }
}
