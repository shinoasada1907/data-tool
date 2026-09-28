package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.DataFormat;
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
@Table(name = "dataset")
class DatasetEntity {

    @Id
    private UUID id;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private DataFormat format;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sheets")
    private String sheetsJson;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    protected DatasetEntity() {
    }

    DatasetEntity(UUID id, String originalFileName, DataFormat format, long sizeBytes, String sheetsJson,
                  Instant createdAt, Instant lastUsedAt) {
        this.id = id;
        this.originalFileName = originalFileName;
        this.format = format;
        this.sizeBytes = sizeBytes;
        this.sheetsJson = sheetsJson;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    UUID id() {
        return id;
    }

    String originalFileName() {
        return originalFileName;
    }

    DataFormat format() {
        return format;
    }

    long sizeBytes() {
        return sizeBytes;
    }

    String sheetsJson() {
        return sheetsJson;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant lastUsedAt() {
        return lastUsedAt;
    }
}
