package com.universaldatatools.platform.run;

import com.universaldatatools.platform.dataset.RetentionProperties;
import com.universaldatatools.platform.storage.FileTrees;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Deletes expired runs, the row first in one conditional statement so a run used meanwhile stays, then its files;
 * files that cannot go become an orphan the storage sweep collects (core-06 RS4). Also deletes staging directories
 * a run abandoned, such as after a crash, once they are an hour old.
 */
@Service
public class RunCleanup {

    /** Runs handled per call; the next call carries on. */
    static final int BATCH = 500;
    /** Older than any run still being written. */
    static final Duration STAGING_AGE = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(RunCleanup.class);

    private final ToolRunJpaRepository jpa;
    private final RunStore store;
    private final RetentionProperties retention;
    private final Clock clock;

    public RunCleanup(ToolRunJpaRepository jpa, RunStore store, RetentionProperties retention, Clock clock) {
        this.jpa = jpa;
        this.store = store;
        this.retention = retention;
        this.clock = clock;
    }

    /** What one call did. */
    public record Report(int deletedRuns, int deletedStaging) {
    }

    public Report cleanup() {
        return cleanup(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    public Report cleanup(Instant now) {
        Instant cutoff = now.minus(retention.runTtl());
        int runs = 0;
        List<UUID> expired = jpa.findIdsLastUsedBefore(cutoff, PageRequest.of(0, BATCH));
        for (UUID id : expired) {
            if (jpa.deleteIfLastUsedBefore(id, cutoff) == 1) {
                runs++;
                store.deleteFiles(id);
            }
        }
        int staging = deleteAbandonedStaging(now);
        if (runs + staging > 0) {
            log.info("Run cleanup: {} runs and {} staging directories deleted", runs, staging);
        }
        return new Report(runs, staging);
    }

    private int deleteAbandonedStaging(Instant now) {
        Path stagingRoot = store.stagingRoot();
        if (!Files.isDirectory(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        int deleted = 0;
        try (Stream<Path> children = Files.list(stagingRoot)) {
            for (Path child : children.toList()) {
                if (Files.getLastModifiedTime(child, LinkOption.NOFOLLOW_LINKS).toInstant()
                        .isBefore(now.minus(STAGING_AGE))) {
                    FileTrees.deleteTree(child);
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.warn("Staging directories could not all be deleted: {}", e.getClass().getName());
        }
        return deleted;
    }
}
