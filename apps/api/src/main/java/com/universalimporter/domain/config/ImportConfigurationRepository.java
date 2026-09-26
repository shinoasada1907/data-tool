package com.universalimporter.domain.config;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ImportConfigurationRepository {

    Optional<ImportConfiguration> findBySessionId(UUID sessionId);

    /** Stores the configuration and returns it as stored, with its new persistence version. */
    ImportConfiguration save(ImportConfiguration configuration, Instant now);
}
