package com.universaldatatools.tools.importer.infrastructure.persistence;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.platform.storage.JpaInstallationRepository;
import com.universaldatatools.support.TestcontainersConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaImportSessionRepository.class, JpaImportConfigurationRepository.class,
        JpaInstallationRepository.class})
class JpaImportSessionRepositoryTest {

    // Micro-second precision: that is what PostgreSQL keeps.
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00.123456Z");
    private static final Instant T1 = Instant.parse("2026-09-25T10:01:00.654321Z");
    private static final Instant T2 = Instant.parse("2026-09-25T10:02:00Z");
    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final SourceFile FILE = new SourceFile("khách hàng.csv", DataFormat.CSV, 7);

    @Autowired
    JpaImportSessionRepository repository;

    @Autowired
    JpaImportConfigurationRepository configurations;

    @Autowired
    JpaInstallationRepository installation;

    @Autowired
    TestEntityManager entityManager;

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Test
    void sessions_last_changed_before_a_cutoff_come_oldest_first_up_to_the_limit() {
        givenSessionsA25hB23hC48hOld();
        Instant cutoff = T0.minus(Duration.ofHours(24));

        assertThat(repository.findIdsUpdatedBefore(cutoff, 10)).containsExactly(C, A);
        assertThat(repository.findIdsUpdatedBefore(cutoff, 1)).containsExactly(C);
    }

    @Test
    void existence_is_checked_by_id() {
        givenSessionsA25hB23hC48hOld();

        assertThat(repository.existsById(A)).isTrue();
        assertThat(repository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void a_session_unchanged_since_the_cutoff_is_deleted_and_an_unknown_one_is_harmless() {
        givenSessionsA25hB23hC48hOld();
        Instant cutoff = T0.minus(Duration.ofHours(24));

        assertThat(repository.deleteIfNotUpdatedSince(A, cutoff)).isTrue();
        assertThat(repository.deleteIfNotUpdatedSince(UUID.randomUUID(), cutoff)).isFalse();
        flushAndClear();

        assertThat(repository.findById(A)).isEmpty();
    }

    @Test
    void a_session_changed_after_the_cutoff_is_kept() {
        givenSessionsA25hB23hC48hOld();

        assertThat(repository.deleteIfNotUpdatedSince(B, T0.minus(Duration.ofHours(24)))).isFalse();
        flushAndClear();

        assertThat(repository.findById(B)).isPresent();
    }

    @Test
    void the_installation_has_one_lasting_id() {
        UUID id = installation.installationId();

        assertThat(id).isNotNull();
        assertThat(installation.installationId()).isEqualTo(id);
    }

    @Test
    void deleting_a_session_deletes_its_configuration() {
        repository.save(ImportSession.create(A, FILE, T0));
        configurations.save(ImportConfiguration.empty(A).withSchema(TargetSchema.define(List.of(
                new FieldSpec("name", "string", true, 0)))).configuration(), T0);
        flushAndClear();

        assertThat(repository.deleteIfNotUpdatedSince(A, T0.plusSeconds(1))).isTrue();
        flushAndClear();

        assertThat(configurations.findBySessionId(A)).isEmpty();
        assertThat(((Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from import_configuration where session_id = :id")
                .setParameter("id", A).getSingleResult()).longValue()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void the_cleanup_query_has_an_index() {
        List<Object> indexes = (List<Object>) entityManager.getEntityManager()
                .createNativeQuery("select indexname from pg_indexes where tablename = 'import_session'")
                .getResultList();

        assertThat(indexes).contains("idx_import_session_updated_at");
    }

    private void givenSessionsA25hB23hC48hOld() {
        repository.save(ImportSession.create(A, FILE, T0.minus(Duration.ofHours(25))));
        repository.save(ImportSession.create(B, FILE, T0.minus(Duration.ofHours(23))));
        repository.save(ImportSession.create(C, FILE, T0.minus(Duration.ofHours(48))));
        flushAndClear();
    }

    @Test
    void reads_back_exactly_what_was_saved() {
        repository.save(ImportSession.create(ID, FILE, T0));
        flushAndClear();

        ImportSession found = repository.findById(ID).orElseThrow();

        assertThat(found.id()).isEqualTo(ID);
        assertThat(found.sourceFile()).isEqualTo(FILE);
        assertThat(found.status()).isEqualTo(SessionStatus.UPLOADED);
        assertThat(found.createdAt()).isEqualTo(T0);
        assertThat(found.updatedAt()).isEqualTo(T0);
        assertThat(found.version()).isZero();
    }

    @Test
    void unknown_id_is_not_found() {
        assertThat(repository.findById(UUID.fromString("11111111-2222-3333-4444-555555555555"))).isEmpty();
    }

    @Test
    void a_session_returned_by_save_can_be_changed_and_saved_again() {
        ImportSession saved = repository.save(ImportSession.create(ID, FILE, T0));
        flushAndClear();
        saved.transitionTo(SessionStatus.CONFIGURING, T1);
        ImportSession savedAgain = repository.save(saved);
        flushAndClear();
        savedAgain.transitionTo(SessionStatus.READY, T2);
        repository.save(savedAgain);
        flushAndClear();

        ImportSession found = repository.findById(ID).orElseThrow();

        assertThat(found.status()).isEqualTo(SessionStatus.READY);
        assertThat(found.updatedAt()).isEqualTo(T2);
        assertThat(found.createdAt()).isEqualTo(T0);
        assertThat(found.version()).isEqualTo(2L);
    }

    @Test
    void source_schema_is_stored_as_a_json_object_and_read_back() {
        SourceSchema schema = new SourceSchema(
                List.of(new Column(0, "name"), new Column(1, "email")), 2, null);
        ImportSession session = ImportSession.create(ID, FILE, T0);
        session.markInspected(schema, T1);
        repository.save(session);
        flushAndClear();

        ImportSession found = repository.findById(ID).orElseThrow();

        assertThat(found.sourceSchema()).contains(schema);
        // A JSON object, not a JSON string holding the text of one (double encoding would still round-trip).
        Object jsonType = entityManager.getEntityManager()
                .createNativeQuery("SELECT jsonb_typeof(source_schema) FROM import_session WHERE id = :id")
                .setParameter("id", ID)
                .getSingleResult();
        assertThat(jsonType).isEqualTo("object");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
