package com.universaldatatools.platform.dataset;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface DatasetJpaRepository extends JpaRepository<DatasetEntity, UUID> {

    /** Moves {@code lastUsedAt} to {@code now} unless it moved less than the touch interval ago. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DatasetEntity d set d.lastUsedAt = :now where d.id = :id and d.lastUsedAt < :before")
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("before") Instant before);

    @Query("select d.id from DatasetEntity d where d.lastUsedAt < :cutoff order by d.lastUsedAt")
    List<UUID> findIdsLastUsedBefore(@Param("cutoff") Instant cutoff, Pageable page);

    /** Checks and deletes in one statement: a dataset used meanwhile is kept. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DatasetEntity d where d.id = :id and d.lastUsedAt < :cutoff")
    int deleteIfLastUsedBefore(@Param("id") UUID id, @Param("cutoff") Instant cutoff);
}
