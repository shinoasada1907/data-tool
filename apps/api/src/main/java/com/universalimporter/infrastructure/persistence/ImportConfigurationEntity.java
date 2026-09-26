package com.universalimporter.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code import_configuration}. The mapping, transformations and validations columns are not mapped
 * until their features need them, so inserts leave them at their database defaults.
 */
@Entity
@Table(name = "import_configuration")
class ImportConfigurationEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    /** {@link TargetSchemaDocument} as JSON text; the adapter owns the (de)serialization. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_schema_json", nullable = false)
    private String targetSchemaJson;

    /** Wrapper type: {@code null} tells Spring Data that an entity with an assigned id is new. */
    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ImportConfigurationEntity() {
    }

    ImportConfigurationEntity(UUID sessionId, String targetSchemaJson, Long version, Instant updatedAt) {
        this.sessionId = sessionId;
        this.targetSchemaJson = targetSchemaJson;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    UUID getSessionId() {
        return sessionId;
    }

    String getTargetSchemaJson() {
        return targetSchemaJson;
    }

    Long getVersion() {
        return version;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
