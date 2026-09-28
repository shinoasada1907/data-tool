package com.universaldatatools.tools.importer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ImportConfigurationJpaRepository extends JpaRepository<ImportConfigurationEntity, UUID> {
}
