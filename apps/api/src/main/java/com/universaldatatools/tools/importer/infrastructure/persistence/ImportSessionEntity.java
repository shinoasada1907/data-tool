package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_session")
class ImportSessionEntity {

    @Id
    private UUID id;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private DataFormat fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    /** Wrapper type: {@code null} tells Spring Data that an entity with an assigned id is new. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@link SourceSchemaDocument} as JSON text; the adapter owns the (de)serialization. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_schema")
    private String sourceSchemaJson;

    protected ImportSessionEntity() {
    }

    ImportSessionEntity(UUID id, String originalFileName, DataFormat fileType, long sizeBytes,
                        SessionStatus status, Long version, Instant createdAt, Instant updatedAt,
                        String sourceSchemaJson) {
        this.id = id;
        this.originalFileName = originalFileName;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sourceSchemaJson = sourceSchemaJson;
    }

    UUID getId() {
        return id;
    }

    String getOriginalFileName() {
        return originalFileName;
    }

    DataFormat getFileType() {
        return fileType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    SessionStatus getStatus() {
        return status;
    }

    Long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    String getSourceSchemaJson() {
        return sourceSchemaJson;
    }
}
