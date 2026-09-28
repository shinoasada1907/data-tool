package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.transform.TransformationStep;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.mapping.MappingSpec;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig;
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

    @Test
    void a_different_mapping_changes_the_hash() {
        assertThat(hasher.hash(mapped(sc("name", "Họ tên"), sc("email", "email"))))
                .isNotEqualTo(hasher.hash(mapped(sc("name", "Họ tên"))));
    }

    @Test
    void the_same_mapping_sent_in_another_order_hashes_the_same() {
        assertThat(hasher.hash(mapped(sc("email", "email"), sc("name", "Họ tên"))))
                .isEqualTo(hasher.hash(mapped(sc("name", "Họ tên"), sc("email", "email"))));
    }

    @Test
    void transformations_are_part_of_the_hash() {
        ImportConfiguration plain = configuration(ID, null, true);
        ImportConfiguration trimmed = plain.withTransformations(
                new TransformationConfig(List.of(new TransformationStep("name", 0, "trim", null)))).configuration();

        assertThat(hasher.hash(trimmed)).isNotEqualTo(hasher.hash(plain));
    }

    @Test
    void validations_are_part_of_the_hash() {
        ImportConfiguration plain = configuration(ID, null, true);
        ImportConfiguration unique = plain.withValidations(
                new ValidationConfig(List.of(new ValidationRuleConfig("email", "unique", null))), List.of()).configuration();

        assertThat(hasher.hash(unique)).isNotEqualTo(hasher.hash(plain));
    }

    @Test
    void the_hashed_json_has_params_in_key_order() {
        // Map.copyOf iterates in an order that changes from one JVM run to the next: only sorted keys keep a
        // hash stable across restarts.
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String key : List.of("h", "g", "f", "e", "d", "c", "b", "a")) {
            params.put(key, key);
        }
        ImportConfiguration configuration = configuration(ID, null, true).withTransformations(
                new TransformationConfig(List.of(new TransformationStep("name", 0, "x", params)))).configuration();

        assertThat(JsonConfigHasher.canonicalJson(configuration)).contains(SORTED_PARAMS);
    }

    @Test
    void the_hash_mapper_itself_sorts_map_keys() {
        // Deterministic, unlike the test above: a LinkedHashMap keeps its reverse order unless the mapper sorts.
        java.util.Map<String, String> reversed = new java.util.LinkedHashMap<>();
        for (String key : List.of("h", "g", "f", "e", "d", "c", "b", "a")) {
            reversed.put(key, key);
        }

        assertThat("\"params\":" + JsonConfigHasher.JSON.writeValueAsString(reversed)).isEqualTo(SORTED_PARAMS);
    }

    private static final String SORTED_PARAMS = "\"params\":{\"a\":\"a\",\"b\":\"b\",\"c\":\"c\",\"d\":\"d\","
            + "\"e\":\"e\",\"f\":\"f\",\"g\":\"g\",\"h\":\"h\"}";

    @Test
    void transformation_params_hash_the_same_whatever_their_order() {
        ImportConfiguration configuration = configuration(ID, null, true);
        java.util.Map<String, String> ab = new java.util.LinkedHashMap<>();
        ab.put("inputFormat", "dd/MM/yyyy");
        ab.put("outputFormat", "yyyy-MM-dd");
        java.util.Map<String, String> ba = new java.util.LinkedHashMap<>();
        ba.put("outputFormat", "yyyy-MM-dd");
        ba.put("inputFormat", "dd/MM/yyyy");

        assertThat(hasher.hash(configuration.withTransformations(new TransformationConfig(List.of(
                new TransformationStep("name", 0, "dateFormat", ab)))).configuration()))
                .isEqualTo(hasher.hash(configuration.withTransformations(new TransformationConfig(List.of(
                        new TransformationStep("name", 0, "dateFormat", ba)))).configuration()));
    }

    private static MappingSpec sc(String target, String column) {
        return new MappingSpec(target, "SOURCE_COLUMN", column, null);
    }

    private static ImportConfiguration mapped(MappingSpec... specs) {
        ImportConfiguration configuration = configuration(ID, null, true);
        SourceSchema source = new SourceSchema(List.of(new Column(0, "Họ tên"), new Column(1, "email")), 1, null);
        return configuration.withMapping(MappingConfig.define(List.of(specs), configuration.schema(), source))
                .configuration();
    }

    private static ImportConfiguration configuration(UUID sessionId, Long version, boolean emailRequired) {
        TargetSchema schema = TargetSchema.define(List.of(
                new FieldSpec("name", "string", false, 0), new FieldSpec("email", "email", emailRequired, 1)));
        return new ImportConfiguration(sessionId, schema, MappingConfig.empty(), TransformationConfig.empty(),
                ValidationConfig.empty(), version);
    }
}
