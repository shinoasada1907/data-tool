package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.importsession.InstallationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reads the single row of {@code installation} (V11). */
@Repository
public class JpaInstallationRepository implements InstallationRepository {

    private final EntityManager entityManager;

    JpaInstallationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID installationId() {
        return (UUID) entityManager.createNativeQuery("select id from installation").getSingleResult();
    }
}
