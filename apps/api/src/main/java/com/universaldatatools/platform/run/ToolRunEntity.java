package com.universaldatatools.platform.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_run")
class ToolRunEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String tool;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String sources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String summary;

    @Version
    private Long version;

    protected ToolRunEntity() {
    }

    ToolRunEntity(UUID id, String tool, Instant createdAt, String sources, String config, String summary) {
        this.id = id;
        this.tool = tool;
        this.createdAt = createdAt;
        this.lastUsedAt = createdAt;
        this.sources = sources;
        this.config = config;
        this.summary = summary;
    }

    UUID id() {
        return id;
    }

    String tool() {
        return tool;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant lastUsedAt() {
        return lastUsedAt;
    }

    String sources() {
        return sources;
    }

    String config() {
        return config;
    }

    String summary() {
        return summary;
    }
}
