package com.universaldatatools.support;

import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfigurationRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test double for the configuration repository; versions count saves like the database does (0, 1, 2…). */
public class InMemoryImportConfigurationRepository implements ImportConfigurationRepository {

    private final Map<UUID, ImportConfiguration> configurations = new HashMap<>();

    @Override
    public Optional<ImportConfiguration> findBySessionId(UUID sessionId) {
        return Optional.ofNullable(configurations.get(sessionId));
    }

    @Override
    public ImportConfiguration save(ImportConfiguration configuration, Instant now) {
        long version = configuration.version() == null ? 0 : configuration.version() + 1;
        ImportConfiguration saved = new ImportConfiguration(configuration.sessionId(), configuration.schema(),
                configuration.mapping(), configuration.transformations(), configuration.validations(), version);
        configurations.put(saved.sessionId(), saved);
        return saved;
    }
}
