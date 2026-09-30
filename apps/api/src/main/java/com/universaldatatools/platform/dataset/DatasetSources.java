package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.platform.storage.FileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How tools read a dataset (core-04 PL5): {@code source: {datasetId, options}} becomes an {@link OpenedSource},
 * inspected with the configured limits (or taken from the inspection cache), locked against deletion until closed,
 * and its use recorded.
 */
@Component
public class DatasetSources {

    private final DatasetRepository datasets;
    private final FileStorage storage;
    private final Map<DataFormat, TableReader> readers;
    private final DatasetInspections inspections;
    private final DatasetLocks locks;
    private final LimitsProperties limits;
    private final Clock clock;

    public DatasetSources(DatasetRepository datasets, FileStorage storage, List<TableReader> readers,
                          DatasetInspections inspections, DatasetLocks locks, LimitsProperties limits, Clock clock) {
        this.datasets = datasets;
        this.storage = storage;
        this.readers = readers.stream().collect(Collectors.toUnmodifiableMap(TableReader::format, Function.identity()));
        this.inspections = inspections;
        this.locks = locks;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * @throws DomainException {@code DATASET_NOT_FOUND}, or the reader's error for these options
     *                         ({@code FILE_PARSE_ERROR}, {@code FILE_EMPTY}, {@code JSON_NOT_FLAT},
     *                         {@code LIMIT_EXCEEDED}, {@code CONFIG_INVALID})
     */
    public OpenedSource open(SourceRef ref) {
        Instant now = now();
        Dataset dataset = datasets.findLive(ref.datasetId(), now).orElseThrow(DatasetSources::notFound);
        DatasetLocks.Held lock = locks.read(dataset.id());
        try {
            // Deleted between the lookup and the lock: the file may already be gone.
            if (!datasets.exists(dataset.id())) {
                throw notFound();
            }
            ReadOptions requested = ref.options() == null ? ReadOptions.defaults() : ref.options();
            ReadOptions options = new ReadOptions(requested.sheet(), requested.delimiter(), requested.encoding(),
                    requested.hasHeader(), limits.toReadLimits());
            TableReader reader = readers.get(dataset.format());
            TableInfo info = inspections.get(dataset.id(), options, () -> inspect(dataset.id(), reader, options));
            datasets.touch(dataset.id(), now);
            return new Opened(dataset, info, reader, lock);
        } catch (RuntimeException e) {
            lock.close();
            throw e;
        }
    }

    static DomainException notFound() {
        return new DomainException(ErrorCode.DATASET_NOT_FOUND, "Dataset not found or expired.");
    }

    private TableInfo inspect(UUID id, TableReader reader, ReadOptions options) {
        try (InputStream in = storage.open(id)) {
            return reader.inspect(in, options);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PostgreSQL keeps microseconds; truncating here keeps memory and database in agreement. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private final class Opened implements OpenedSource {

        private final Dataset dataset;
        private final TableInfo info;
        private final TableReader reader;
        private final DatasetLocks.Held lock;

        Opened(Dataset dataset, TableInfo info, TableReader reader, DatasetLocks.Held lock) {
            this.dataset = dataset;
            this.info = info;
            this.reader = reader;
            this.lock = lock;
        }

        @Override
        public Dataset dataset() {
            return dataset;
        }

        @Override
        public TableInfo info() {
            return info;
        }

        @Override
        public Stream<Row> rows() {
            return reader.read(storage.open(dataset.id()), info);
        }

        @Override
        public void close() {
            lock.close();
        }
    }
}
