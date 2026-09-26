package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.ImportConfigurationRepository;
import com.universalimporter.domain.mapping.MappingConfig;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaImportConfigurationRepository implements ImportConfigurationRepository {

    /** Own mapper rather than the application's, as in {@link JpaImportSessionRepository} (design D8). */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ImportConfigurationJpaRepository jpa;

    JpaImportConfigurationRepository(ImportConfigurationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ImportConfiguration> findBySessionId(UUID sessionId) {
        return jpa.findById(sessionId).map(JpaImportConfigurationRepository::toDomain);
    }

    @Override
    public ImportConfiguration save(ImportConfiguration configuration, Instant now) {
        String schemaJson = JSON.writeValueAsString(TargetSchemaDocument.from(configuration.schema()));
        ImportConfigurationEntity entity =
                new ImportConfigurationEntity(configuration.sessionId(), schemaJson, configuration.version(), now);
        // Flush now so the returned configuration carries the version the database really holds.
        return toDomain(jpa.saveAndFlush(entity));
    }

    private static ImportConfiguration toDomain(ImportConfigurationEntity entity) {
        TargetSchemaDocument schema = JSON.readValue(entity.getTargetSchemaJson(), TargetSchemaDocument.class);
        return new ImportConfiguration(entity.getSessionId(), schema.toDomain(), MappingConfig.empty(),
                entity.getVersion());
    }
}
