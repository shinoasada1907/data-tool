package com.universalimporter.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ImportSessionJpaRepository extends JpaRepository<ImportSessionEntity, UUID> {
}
