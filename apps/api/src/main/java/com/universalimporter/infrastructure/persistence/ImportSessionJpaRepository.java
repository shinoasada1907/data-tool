package com.universalimporter.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import java.util.UUID;

interface ImportSessionJpaRepository extends JpaRepository<ImportSessionEntity, UUID> {

    @Query("select s.id from ImportSessionEntity s where s.updatedAt < :cutoff order by s.updatedAt")
    List<UUID> findIdsUpdatedBefore(@Param("cutoff") Instant cutoff, Pageable page);

    /** A bulk delete: the database cascades to {@code import_configuration} (V3). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ImportSessionEntity s where s.id = :id")
    void deleteSession(@Param("id") UUID id);
}
