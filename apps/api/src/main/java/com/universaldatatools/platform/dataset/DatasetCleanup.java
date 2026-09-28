package com.universaldatatools.platform.dataset;

import com.universaldatatools.platform.storage.FileStorage;
import com.universaldatatools.platform.storage.StorageOwner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Deletes expired datasets (core-04 PL9): the row first, in one conditional statement so a dataset used meanwhile
 * stays, then its files. Files that cannot go become an orphan the storage sweep collects. A dataset being read is
 * skipped until the next run. Its directories are never orphans while its row exists.
 */
@Service
public class DatasetCleanup implements StorageOwner {

    /** Datasets handled per run; the next run carries on. */
    static final int BATCH = 500;

    private static final Logger log = LoggerFactory.getLogger(DatasetCleanup.class);

    private final DatasetRepository datasets;
    private final FileStorage storage;
    private final DatasetInspections inspections;
    private final DatasetLocks locks;
    private final Clock clock;

    public DatasetCleanup(DatasetRepository datasets, FileStorage storage, DatasetInspections inspections,
                          DatasetLocks locks, Clock clock) {
        this.datasets = datasets;
        this.storage = storage;
        this.inspections = inspections;
        this.locks = locks;
        this.clock = clock;
    }

    /** What one run did. */
    public record Report(int deleted, int skipped, int failures) {
    }

    @Override
    public boolean owns(UUID id) {
        return datasets.exists(id);
    }

    public Report cleanupExpired() {
        return cleanupExpired(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    public Report cleanupExpired(Instant now) {
        int deleted = 0;
        int skipped = 0;
        int failures = 0;
        for (UUID id : datasets.listExpired(now, BATCH)) {
            Optional<DatasetLocks.Held> lock = locks.tryWrite(id, Duration.ZERO);
            if (lock.isEmpty()) {
                skipped++;
                continue;
            }
            try (DatasetLocks.Held held = lock.get()) {
                if (datasets.deleteIfExpired(id, now)) {
                    inspections.evict(id);
                    deleted++;
                    deleteFiles(id);
                }
            } catch (RuntimeException e) {
                failures++;
                log.warn("Cleanup failed for dataset {}: {}", id, e.getClass().getName());
            }
        }
        if (deleted + skipped + failures > 0) {
            log.info("Dataset cleanup: {} deleted, {} skipped, {} failed", deleted, skipped, failures);
        }
        return new Report(deleted, skipped, failures);
    }

    private void deleteFiles(UUID id) {
        try {
            storage.delete(id);
        } catch (RuntimeException e) {
            log.warn("Files of dataset {} could not be deleted; the orphan sweep will: {}", id,
                    e.getClass().getName());
        }
    }
}
