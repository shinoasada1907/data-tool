package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConfigHasherTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    private final JsonConfigHasher hasher = new JsonConfigHasher();

    @Test
    void the_same_configuration_always_has_the_same_sha256_hex() {
        ImportConfiguration configuration = configuration(ID, null, true);

        String first = hasher.hash(configuration);

        assertThat(hasher.hash(configuration)).isEqualTo(first);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void any_content_change_changes_the_hash() {
        assertThat(hasher.hash(configuration(ID, null, true))).isNotEqualTo(hasher.hash(configuration(ID, null, false)));
    }

    @Test
    void session_and_version_are_not_part_of_the_hash() {
        UUID other = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertThat(hasher.hash(configuration(ID, null, true))).isEqualTo(hasher.hash(configuration(other, 7L, true)));
    }

    private static ImportConfiguration configuration(UUID sessionId, Long version, boolean emailRequired) {
        TargetSchema schema = TargetSchema.define(List.of(
                new FieldSpec("name", "string", false, 0), new FieldSpec("email", "email", emailRequired, 1)));
        return new ImportConfiguration(sessionId, schema, version);
    }
}
