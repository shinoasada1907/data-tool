package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.SourceFile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaImportSessionRepository implements ImportSessionRepository {

    private final ImportSessionJpaRepository jpa;

    JpaImportSessionRepository(ImportSessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ImportSession save(ImportSession session) {
        // Flush now so the returned session carries the version the database really holds;
        // saving it again later would otherwise fail the optimistic lock check.
        return toDomain(jpa.saveAndFlush(toEntity(session)));
    }

    @Override
    public Optional<ImportSession> findById(UUID id) {
        return jpa.findById(id).map(JpaImportSessionRepository::toDomain);
    }

    private static ImportSessionEntity toEntity(ImportSession session) {
        SourceFile file = session.sourceFile();
        return new ImportSessionEntity(session.id(), file.originalFileName(), file.fileType(), file.sizeBytes(),
                session.status(), session.version(), session.createdAt(), session.updatedAt());
    }

    private static ImportSession toDomain(ImportSessionEntity entity) {
        SourceFile file = new SourceFile(entity.getOriginalFileName(), entity.getFileType(), entity.getSizeBytes());
        return ImportSession.restore(entity.getId(), file, entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
}
