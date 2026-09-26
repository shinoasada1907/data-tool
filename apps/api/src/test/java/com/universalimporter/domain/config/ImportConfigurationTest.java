package com.universalimporter.domain.config;

import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportConfigurationTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    @Test
    void empty_has_no_schema_fields_and_no_version() {
        ImportConfiguration empty = ImportConfiguration.empty(ID);

        assertThat(empty.sessionId()).isEqualTo(ID);
        assertThat(empty.schema().isEmpty()).isTrue();
        assertThat(empty.version()).isNull();
    }

    @Test
    void with_schema_replaces_the_schema_and_keeps_the_version() {
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("email", "email", true, 0)));

        ConfigChange change = ImportConfiguration.empty(ID).withSchema(schema);

        assertThat(change.configuration().schema()).isEqualTo(schema);
        assertThat(change.configuration().sessionId()).isEqualTo(ID);
        assertThat(change.configuration().version()).isNull();
        assertThat(change.warnings()).isEmpty();
    }
}
