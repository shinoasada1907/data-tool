package com.universalimporter.domain.config;

import com.universalimporter.domain.schema.TargetSchema;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything configured for one session, kept apart from the session itself (design S2). A session without a
 * stored configuration has the {@link #empty} one.
 *
 * @param version persistence version, {@code null} until first stored
 */
public record ImportConfiguration(UUID sessionId, TargetSchema schema, Long version) {

    public ImportConfiguration {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(schema, "schema");
    }

    public static ImportConfiguration empty(UUID sessionId) {
        return new ImportConfiguration(sessionId, TargetSchema.empty(), null);
    }

    /**
     * Replaces the schema and prunes every section that refers to fields it no longer has (design S3).
     * No section exists yet, so there is nothing to prune.
     */
    public ConfigChange withSchema(TargetSchema schema) {
        return new ConfigChange(new ImportConfiguration(sessionId, schema, version), List.of());
    }
}
