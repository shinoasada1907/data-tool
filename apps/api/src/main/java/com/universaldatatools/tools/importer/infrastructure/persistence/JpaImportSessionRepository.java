package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.ImportSessionRepository;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.core.table.SourceSchema;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaImportSessionRepository implements ImportSessionRepository {

    /**
     * Own mapper rather than the application's: the stored JSON must not change when API JSON settings do
     * (design D8).
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Override
    public List<UUID> findIdsUpdatedBefore(Instant cutoff, int limit) {
        return jpa.findIdsUpdatedBefore(cutoff, PageRequest.of(0, limit));
    }

    @Override
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    public boolean deleteIfNotUpdatedSince(UUID id, Instant cutoff) {
        return jpa.deleteIfNotUpdatedSince(id, cutoff) == 1;
    }

    private static ImportSessionEntity toEntity(ImportSession session) {
        SourceFile file = session.sourceFile();
        String sourceSchemaJson = session.sourceSchema()
                .map(schema -> JSON.writeValueAsString(SourceSchemaDocument.from(schema)))
                .orElse(null);
        return new ImportSessionEntity(session.id(), file.originalFileName(), file.fileType(), file.sizeBytes(),
                session.status(), session.version(), session.createdAt(), session.updatedAt(), sourceSchemaJson);
    }

    private static ImportSession toDomain(ImportSessionEntity entity) {
        SourceFile file = new SourceFile(entity.getOriginalFileName(), entity.getFileType(), entity.getSizeBytes());
        SourceSchema sourceSchema = entity.getSourceSchemaJson() == null
                ? null
                : JSON.readValue(entity.getSourceSchemaJson(), SourceSchemaDocument.class).toDomain();
        return ImportSession.restore(entity.getId(), file, entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion(), sourceSchema);
    }
}
