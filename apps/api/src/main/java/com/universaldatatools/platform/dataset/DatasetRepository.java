package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.SheetInfo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code dataset} table (core-04 PL5). A dataset whose last use is older than the retention period is gone for
 * every reader, even before the cleanup deletes it.
 */
@Component
public class DatasetRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<SheetInfo>> SHEETS = new TypeReference<>() {
    };

    private final DatasetJpaRepository jpa;
    private final RetentionProperties retention;

    public DatasetRepository(DatasetJpaRepository jpa, RetentionProperties retention) {
        this.jpa = jpa;
        this.retention = retention;
    }

    public void insert(Dataset dataset) {
        jpa.save(new DatasetEntity(dataset.id(), dataset.originalFileName(), dataset.format(), dataset.sizeBytes(),
                dataset.sheets() == null ? null : JSON.writeValueAsString(dataset.sheets()), dataset.createdAt(),
                dataset.lastUsedAt()));
    }

    /** The dataset if it exists and has not expired at {@code now}. */
    public Optional<Dataset> findLive(UUID id, Instant now) {
        return jpa.findById(id).map(DatasetRepository::toDataset)
                .filter(dataset -> dataset.lastUsedAt().isAfter(now.minus(retention.datasetTtl())));
    }

    /** Records a use at {@code now}, at most once per touch interval. */
    public void touch(UUID id, Instant now) {
        jpa.touch(id, now, now.minus(retention.touchInterval()));
    }

    public boolean exists(UUID id) {
        return jpa.existsById(id);
    }

    public long count() {
        return jpa.count();
    }

    public List<UUID> listExpired(Instant now, int limit) {
        return jpa.findIdsLastUsedBefore(expiryCutoff(now), PageRequest.of(0, limit));
    }

    /** Deletes the row only if it is still expired; {@code false} when it was used meanwhile or is gone. */
    public boolean deleteIfExpired(UUID id, Instant now) {
        return jpa.deleteIfLastUsedBefore(id, expiryCutoff(now)) == 1;
    }

    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    public Instant expiresAt(Dataset dataset) {
        return dataset.lastUsedAt().plus(retention.datasetTtl());
    }

    public Duration touchInterval() {
        return retention.touchInterval();
    }

    private Instant expiryCutoff(Instant now) {
        return now.minus(retention.datasetTtl());
    }

    private static Dataset toDataset(DatasetEntity entity) {
        List<SheetInfo> sheets = entity.sheetsJson() == null ? null : JSON.readValue(entity.sheetsJson(), SHEETS);
        return new Dataset(entity.id(), entity.originalFileName(), entity.format(), entity.sizeBytes(), sheets,
                entity.createdAt(), entity.lastUsedAt());
    }
}
