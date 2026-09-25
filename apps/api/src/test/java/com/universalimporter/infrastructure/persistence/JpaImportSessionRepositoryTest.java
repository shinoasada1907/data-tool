package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaImportSessionRepository.class})
class JpaImportSessionRepositoryTest {

    // Micro-second precision: that is what PostgreSQL keeps.
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00.123456Z");
    private static final Instant T1 = Instant.parse("2026-09-25T10:01:00.654321Z");
    private static final Instant T2 = Instant.parse("2026-09-25T10:02:00Z");
    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final SourceFile FILE = new SourceFile("khách hàng.csv", SourceFileType.CSV, 7);

    @Autowired
    JpaImportSessionRepository repository;

    @Autowired
    TestEntityManager entityManager;

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

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
