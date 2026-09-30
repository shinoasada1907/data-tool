package com.universaldatatools.tools.converter.application;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.format.xlsx.XlsxTableWriter;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ColumnProfile;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.OutputColumn;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.RowSink;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TableWriter;
import com.universaldatatools.core.table.TypedCell;
import com.universaldatatools.platform.dataset.DatasetSources;
import com.universaldatatools.platform.dataset.OpenedSource;
import com.universaldatatools.platform.dataset.SourceRef;
import com.universaldatatools.platform.output.DownloadNames;
import com.universaldatatools.platform.output.Downloads;
import com.universaldatatools.platform.output.OutputDto;
import com.universaldatatools.platform.output.OutputSpec;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a dataset to CSV, XLSX or JSON (tool-02 CV1–CV4). {@link #prepare} does every check before a byte is
 * written; the returned body then streams the rows, each cell with its source kind so the writer's typing decides.
 */
@Service
public class ConverterService {

    private final DatasetSources sources;
    private final Map<DataFormat, TableWriter> writers;

    public ConverterService(DatasetSources sources, List<TableWriter> writers) {
        this.sources = sources;
        this.writers = writers.stream().collect(Collectors.toUnmodifiableMap(TableWriter::format, Function.identity()));
    }

    /** A conversion ready to stream; closing it releases the dataset. */
    public record Prepared(String fileName, String contentType, Downloads.BodyWriter body, OpenedSource source)
            implements AutoCloseable {

        @Override
        public void close() {
            source.close();
        }
    }

    /**
     * @throws DomainException {@code CONFIG_INVALID} (output), {@code DATASET_NOT_FOUND}, the reader's error for the
     *                         source options, or {@code LIMIT_EXCEEDED} when XLSX cannot hold the dataset
     */
    public Prepared prepare(SourceRef source, OutputDto output) {
        OutputSpec.from(output, null);
        OpenedSource opened = sources.open(source);
        try {
            TableInfo info = opened.info();
            OutputSpec spec = OutputSpec.from(output, info.format() == DataFormat.XLSX ? info.sheetName() : null);
            if (spec.format() == DataFormat.XLSX) {
                checkXlsx(info);
            }
            TableWriter writer = writers.get(spec.format());
            List<OutputColumn> columns = columns(info);
            String fileName = DownloadNames.of(opened.dataset().originalFileName(), "." + writer.extension());
            Downloads.BodyWriter body = out -> {
                RowSink sink = writer.open(out, columns, spec.options());
                try (Stream<Row> rows = opened.rows()) {
                    Iterator<Row> iterator = rows.iterator();
                    while (iterator.hasNext()) {
                        sink.write(cells(iterator.next(), columns.size()));
                    }
                } catch (IOException | RuntimeException e) {
                    sink.abort();
                    throw e;
                }
                sink.close();
            };
            return new Prepared(fileName, writer.contentType(), body, opened);
        } catch (RuntimeException e) {
            opened.close();
            throw e;
        }
    }

    private static void checkXlsx(TableInfo info) {
        if (info.rowCount() > XlsxTableWriter.MAX_ROWS) {
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "XLSX holds at most " + XlsxTableWriter.MAX_ROWS
                    + " rows; the dataset has " + info.rowCount() + ".");
        }
        for (int i = 0; i < info.profiles().size(); i++) {
            if (info.profiles().get(i).maxLength() > XlsxTableWriter.MAX_CELL_LENGTH) {
                throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Column \"" + info.columns().get(i).name()
                        + "\" has a value longer than " + XlsxTableWriter.MAX_CELL_LENGTH
                        + " characters, more than an XLSX cell holds.");
            }
        }
    }

    private static List<OutputColumn> columns(TableInfo info) {
        List<OutputColumn> columns = new ArrayList<>(info.columns().size());
        for (int i = 0; i < info.columns().size(); i++) {
            Column column = info.columns().get(i);
            ColumnProfile profile = i < info.profiles().size() ? info.profiles().get(i) : null;
            columns.add(new OutputColumn(column.name(), profile));
        }
        return columns;
    }

    private static List<TypedCell> cells(Row row, int width) {
        List<TypedCell> cells = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            cells.add(new TypedCell(row.value(i), row.kind(i)));
        }
        return cells;
    }
}
