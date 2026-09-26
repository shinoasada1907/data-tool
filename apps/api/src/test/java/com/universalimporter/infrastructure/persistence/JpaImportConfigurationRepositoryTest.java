package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.support.TestcontainersConfiguration;
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
        sessions.save(ImportSession.create(ID, new SourceFile("customers.csv", SourceFileType.CSV, 7), T0));
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
