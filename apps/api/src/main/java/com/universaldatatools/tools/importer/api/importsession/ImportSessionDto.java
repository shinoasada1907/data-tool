package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.tools.importer.application.importsession.SessionDetails;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;

import java.time.Instant;
import java.util.UUID;

/** {@code ImportSessionDto} of the API contract V0.1 (be-f01 design.md), with config and readiness from BE-F04. */
public record ImportSessionDto(
        UUID id,
        SessionStatus status,
        String originalFileName,
        SourceFileType fileType,
        long sizeBytes,
        Instant createdAt,
        Instant updatedAt,
        SessionConfigDto config,
        ReadinessDto readiness) {

    public static ImportSessionDto from(SessionDetails details) {
        ImportSession session = details.session();
        return new ImportSessionDto(
                session.id(),
                session.status(),
                session.sourceFile().originalFileName(),
                session.sourceFile().fileType(),
                session.sourceFile().sizeBytes(),
                session.createdAt(),
                session.updatedAt(),
                SessionConfigDto.from(details.configuration()),
                ReadinessDto.from(details.readiness()));
    }
}
