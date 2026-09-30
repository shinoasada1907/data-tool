package com.universaldatatools.platform.run;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A committed run. {@code config} and {@code summary} are the tool's own JSON; the store only keeps them (core-06 RS3).
 */
public record RunRecord(UUID id, String tool, Instant createdAt, Instant expiresAt, List<RunSource> sources,
                        JsonNode config, JsonNode summary) {
}
