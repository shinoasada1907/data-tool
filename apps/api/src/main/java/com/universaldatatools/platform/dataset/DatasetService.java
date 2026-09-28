package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.OriginalFileName;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.FileTypeDetector;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableReader;
import com.universaldatatools.platform.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Upload, read, preview and delete toolbox datasets (core-04 PL5, spec: dataset-api). A failed upload leaves no row
 * and no file behind.
 */
@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    /** How long a delete waits for readers to finish before answering SERVER_BUSY. */
    static final Duration DELETE_WAIT = Duration.ofSeconds(5);

    private final DatasetRepository datasets;
    private final FileStorage storage;
    private final List<TableReader> readers;
    private final DatasetSources sources;
    private final DatasetInspections inspections;
    private final DatasetLocks locks;
    private final Clock clock;

    public DatasetService(DatasetRepository datasets, FileStorage storage, List<TableReader> readers,
                          DatasetSources sources, DatasetInspections inspections, DatasetLocks locks, Clock clock) {
        this.datasets = datasets;
        this.storage = storage;
        this.readers = List.copyOf(readers);
        this.sources = sources;
        this.inspections = inspections;
        this.locks = locks;
        this.clock = clock;
    }

    /** A preview: what the read found and its first rows. */
    public record Preview(Dataset dataset, TableInfo info, List<Row> rows, int limit) {
    }

    /**
     * @throws DomainException {@code FILE_UNSUPPORTED}, {@code FILE_EMPTY} or {@code FILE_PARSE_ERROR} (a broken
     *                         workbook, JSON that is not an array)
     */
    public Dataset upload(String originalFileName, InputStream content) {
        String name = OriginalFileName.sanitize(originalFileName);
        BufferedInputStream in = new BufferedInputStream(content, FileTypeDetector.HEAD_SIZE);
        DataFormat format = FileTypeDetector.detectDataset(name, head(in));
        UUID id = UUID.randomUUID();
        long size = storage.save(id, in);
        try {
            List<SheetInfo> sheets = format == DataFormat.XLSX ? sheets(id) : null;
            Instant now = now();
            Dataset dataset = new Dataset(id, name, format, size, sheets, now, now);
            datasets.insert(dataset);
            log.info("Uploaded dataset {}: {} bytes of {}", id, size, format);
            return dataset;
        } catch (RuntimeException e) {
            deleteFiles(id);
            throw e;
        }
    }

    /** @throws DomainException {@code DATASET_NOT_FOUND} */
    public Dataset get(UUID id) {
        Instant now = now();
        Dataset dataset = datasets.findLive(id, now).orElseThrow(DatasetSources::notFound);
        datasets.touch(id, now);
        return dataset;
    }

    /** @throws DomainException {@code DATASET_NOT_FOUND}, or the reader's error for these options */
    public Preview preview(UUID id, ReadOptions options, int limit) {
        try (OpenedSource source = sources.open(new SourceRef(id, options));
             Stream<Row> rows = source.rows()) {
            return new Preview(source.dataset(), source.info(), rows.limit(limit).toList(), limit);
        }
    }

    /**
     * Row first, then the file: a file that cannot go becomes an orphan the cleanup collects.
     *
     * @throws DomainException {@code DATASET_NOT_FOUND}, or {@code SERVER_BUSY} while a reader holds the dataset
     */
    public void delete(UUID id) {
        datasets.findLive(id, now()).orElseThrow(DatasetSources::notFound);
        DatasetLocks.Held lock = locks.tryWrite(id, DELETE_WAIT).orElseThrow(() -> DomainException.retryLater(
                ErrorCode.SERVER_BUSY, "Dataset is being read; try again shortly.", 5));
        try (lock) {
            datasets.delete(id);
            inspections.evict(id);
            deleteFiles(id);
        }
    }

    public Instant expiresAt(Dataset dataset) {
        return datasets.expiresAt(dataset);
    }

    private List<SheetInfo> sheets(UUID id) {
        TableReader xlsx = readers.stream().filter(reader -> reader.format() == DataFormat.XLSX).findFirst()
                .orElseThrow(() -> new IllegalStateException("No XLSX reader"));
        try (InputStream file = storage.open(id)) {
            return xlsx.sheets(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] head(BufferedInputStream in) {
        try {
            in.mark(FileTypeDetector.HEAD_SIZE);
            byte[] head = in.readNBytes(FileTypeDetector.HEAD_SIZE);
            in.reset();
            return head;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteFiles(UUID id) {
        try {
            storage.delete(id);
        } catch (RuntimeException e) {
            log.warn("Files of dataset {} could not be deleted; the cleanup will: {}", id, e.getClass().getName());
        }
    }

    /** PostgreSQL keeps microseconds; truncating here keeps memory and database in agreement. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
