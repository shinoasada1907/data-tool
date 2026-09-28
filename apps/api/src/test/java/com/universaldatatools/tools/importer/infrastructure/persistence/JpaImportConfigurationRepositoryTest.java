package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.core.transform.TransformationStep;
import com.universaldatatools.support.TestcontainersConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.mapping.MappingSpec;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaImportConfigurationRepository.class, JpaImportSessionRepository.class})
class JpaImportConfigurationRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-09-26T10:00:00.123456Z");
    private static final Instant T1 = Instant.parse("2026-09-26T10:01:00Z");
    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", false, 0), new FieldSpec("email", "email", true, 1)));

    @Autowired
    JpaImportConfigurationRepository repository;

    @Autowired
    JpaImportSessionRepository sessions;

    @Autowired
    TestEntityManager entityManager;

    @BeforeEach
    void storeTheSession() {
        sessions.save(ImportSession.create(ID, new SourceFile("customers.csv", DataFormat.CSV, 7), T0));
    }

    @Test
    void reads_back_the_saved_schema_at_version_zero() {
        repository.save(ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration(), T0);
        flushAndClear();

        ImportConfiguration found = repository.findBySessionId(ID).orElseThrow();

        assertThat(found.sessionId()).isEqualTo(ID);
        assertThat(found.schema()).isEqualTo(SCHEMA);
        assertThat(found.version()).isZero();
    }

    @Test
    void a_stored_schema_hashes_like_the_same_schema_defined_again() {
        // What keeps a PROCESSED session processed when the client re-sends the schema it already has.
        repository.save(ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration(), T0);
        flushAndClear();
        JsonConfigHasher hasher = new JsonConfigHasher();
        ImportConfiguration redefined = ImportConfiguration.empty(ID).withSchema(TargetSchema.define(List.of(
                new FieldSpec(" name ", "string", false, 7), new FieldSpec("email", "email", true, 9)))).configuration();

        assertThat(hasher.hash(repository.findBySessionId(ID).orElseThrow())).isEqualTo(hasher.hash(redefined));
    }

    @Test
    void reads_back_the_saved_mapping() {
        TargetSchema schema = TargetSchema.define(List.of(
                new FieldSpec("name", "string", true, 0), new FieldSpec("country", "string", false, 1)));
        SourceSchema source = new SourceSchema(List.of(new Column(0, "Họ tên")), 1, null);
        MappingConfig mapping = MappingConfig.define(List.of(
                new MappingSpec("name", "SOURCE_COLUMN", "Họ tên", null),
                new MappingSpec("country", "CONSTANT", null, "VN")), schema, source);
        ImportConfiguration configuration =
                ImportConfiguration.empty(ID).withSchema(schema).configuration().withMapping(mapping).configuration();

        repository.save(configuration, T0);
        flushAndClear();

        assertThat(repository.findBySessionId(ID).orElseThrow().mapping()).isEqualTo(mapping);
    }

    @Test
    void reads_back_the_saved_transformations() {
        TransformationConfig transformations = new TransformationConfig(List.of(
                new TransformationStep("name", 0, "trim", null),
                new TransformationStep("email", 0, "dateFormat", java.util.Map.of("inputFormat", "dd/MM/yyyy"))));
        ImportConfiguration configuration = ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration()
                .withTransformations(transformations).configuration();

        repository.save(configuration, T0);
        flushAndClear();

        assertThat(repository.findBySessionId(ID).orElseThrow().transformations()).isEqualTo(transformations);
    }

    @Test
    void reads_back_the_saved_validations() {
        ValidationConfig validations = new ValidationConfig(List.of(
                new ValidationRuleConfig("name", "unique", null), new ValidationRuleConfig("email", "unique", null)));
        ImportConfiguration configuration = ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration()
                .withValidations(validations, List.of()).configuration();

        repository.save(configuration, T0);
        flushAndClear();

        assertThat(repository.findBySessionId(ID).orElseThrow().validations()).isEqualTo(validations);
    }

    @Test
    void a_row_stored_before_mappings_existed_has_the_empty_mapping() {
        // What an F04 row looks like: mapping_json left at its column default.
        entityManager.getEntityManager().createNativeQuery("INSERT INTO import_configuration "
                        + "(session_id, target_schema_json, version, updated_at) VALUES (:id, '{\"fields\": []}', 0, now())")
                .setParameter("id", ID)
                .executeUpdate();

        assertThat(repository.findBySessionId(ID).orElseThrow().mapping()).isEqualTo(MappingConfig.empty());
        assertThat(repository.findBySessionId(ID).orElseThrow().transformations()).isEqualTo(TransformationConfig.empty());
        assertThat(repository.findBySessionId(ID).orElseThrow().validations()).isEqualTo(ValidationConfig.empty());
    }

    @Test
    void a_session_without_configuration_is_not_found() {
        assertThat(repository.findBySessionId(UUID.fromString("11111111-2222-3333-4444-555555555555"))).isEmpty();
    }

    @Test
    void saving_what_was_read_replaces_the_schema_and_bumps_the_version() {
        repository.save(ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration(), T0);
        flushAndClear();
        TargetSchema phone = TargetSchema.define(List.of(new FieldSpec("phone", "string", false, 0)));
        ImportConfiguration read = repository.findBySessionId(ID).orElseThrow();

        repository.save(read.withSchema(phone).configuration(), T1);
        flushAndClear();

        ImportConfiguration found = repository.findBySessionId(ID).orElseThrow();
        assertThat(found.schema()).isEqualTo(phone);
        assertThat(found.version()).isEqualTo(1L);
    }

    @Test
    void a_configuration_needs_an_existing_session() {
        UUID unknown = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertThatThrownBy(() -> repository.save(ImportConfiguration.empty(unknown).withSchema(SCHEMA).configuration(), T0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void columns_of_later_features_keep_their_defaults() {
        repository.save(ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration(), T0);
        flushAndClear();

        Object[] row = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("SELECT mapping_json::text, transformations_json::text, validations_json::text, "
                        + "jsonb_typeof(target_schema_json), updated_at = :t0 FROM import_configuration WHERE session_id = :id")
                .setParameter("id", ID)
                .setParameter("t0", T0)
                .getSingleResult();

        assertThat(row[0]).isEqualTo("{\"mappings\": []}");
        assertThat(row[1]).isEqualTo("{\"transformations\": []}");
        assertThat(row[2]).isEqualTo("{\"validations\": []}");
        // A JSON object, not a JSON string holding the text of one.
        assertThat(row[3]).isEqualTo("object");
        assertThat(row[4]).isEqualTo(true);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
