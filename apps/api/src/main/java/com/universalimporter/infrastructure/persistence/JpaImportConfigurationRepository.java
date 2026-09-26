package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.ImportConfigurationRepository;
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
        String mappingJson = JSON.writeValueAsString(MappingDocument.from(configuration.mapping()));
        String transformationsJson =
                JSON.writeValueAsString(TransformationsDocument.from(configuration.transformations()));
        String validationsJson = JSON.writeValueAsString(ValidationsDocument.from(configuration.validations()));
        ImportConfigurationEntity entity = new ImportConfigurationEntity(configuration.sessionId(), schemaJson,
                mappingJson, transformationsJson, validationsJson, configuration.version(), now);
        // Flush now so the returned configuration carries the version the database really holds.
        return toDomain(jpa.saveAndFlush(entity));
    }

    private static ImportConfiguration toDomain(ImportConfigurationEntity entity) {
        TargetSchemaDocument schema = JSON.readValue(entity.getTargetSchemaJson(), TargetSchemaDocument.class);
        MappingDocument mapping = JSON.readValue(entity.getMappingJson(), MappingDocument.class);
        TransformationsDocument transformations =
                JSON.readValue(entity.getTransformationsJson(), TransformationsDocument.class);
        ValidationsDocument validations = JSON.readValue(entity.getValidationsJson(), ValidationsDocument.class);
        return new ImportConfiguration(entity.getSessionId(), schema.toDomain(), mapping.toDomain(),
                transformations.toDomain(), validations.toDomain(), entity.getVersion());
    }
}
