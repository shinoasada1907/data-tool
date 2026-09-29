package com.universaldatatools.platform.run;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ToolRunJpaRepository extends JpaRepository<ToolRunEntity, UUID> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ToolRunEntity r set r.lastUsedAt = :now where r.id = :id and r.lastUsedAt < :before")
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("before") Instant before);

    @Query("select r.id from ToolRunEntity r where r.lastUsedAt < :cutoff order by r.lastUsedAt")
    List<UUID> findIdsLastUsedBefore(@Param("cutoff") Instant cutoff, Pageable page);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ToolRunEntity r where r.id = :id and r.lastUsedAt < :cutoff")
    int deleteIfLastUsedBefore(@Param("id") UUID id, @Param("cutoff") Instant cutoff);
}
