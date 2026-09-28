package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.ColumnProfile;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.ResolvedReadOptions;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SheetInfo;
import com.universaldatatools.core.table.TableInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The dataset DTOs of the API contract Toolbox v1 (core-01 design). */
public final class DatasetDtos {

    private DatasetDtos() {
    }

    public record DatasetDto(UUID id, String originalFileName, DataFormat format, long sizeBytes,
                             List<SheetDto> sheets, Instant createdAt, Instant expiresAt) {

        static DatasetDto of(Dataset dataset, Instant expiresAt) {
            return new DatasetDto(dataset.id(), dataset.originalFileName(), dataset.format(), dataset.sizeBytes(),
                    sheetDtos(dataset.sheets()), dataset.createdAt(), expiresAt);
        }
    }

    public record SheetDto(String name, boolean visible) {
    }

    /** The options a read used; {@code delimiter} and {@code encoding} as the API names them. */
    public record OptionsDto(String sheet, String delimiter, String encoding, boolean hasHeader) {

        public static OptionsDto of(ResolvedReadOptions options) {
            return new OptionsDto(options.sheet(), options.delimiter() == null ? null : options.delimiter().name(),
                    options.encoding() == null ? null : options.encoding().apiName(), options.hasHeader());
        }
    }

    public record ColumnDto(int index, String name, String inferredType, long emptyCount) {
    }

    public record RowDto(long rowNumber, List<String> values) {
    }

    public record DatasetPreviewDto(UUID datasetId, DataFormat format, OptionsDto options, List<String> autoDetected,
                                    String sheetName, List<SheetDto> sheets, List<ColumnDto> columns, long totalRows,
                                    long blankRowsSkipped, int previewLimit, List<RowDto> rows) {

        static DatasetPreviewDto of(DatasetService.Preview preview) {
            TableInfo info = preview.info();
            List<ColumnDto> columns = new ArrayList<>(info.columns().size());
            for (int i = 0; i < info.columns().size(); i++) {
                Column column = info.columns().get(i);
                ColumnProfile profile = i < info.profiles().size() ? info.profiles().get(i) : null;
                columns.add(new ColumnDto(column.index(), column.name(),
                        profile == null ? null : profile.inferredType().apiName(),
                        profile == null ? 0 : profile.emptyCount()));
            }
            return new DatasetPreviewDto(preview.dataset().id(), info.format(), OptionsDto.of(info.options()),
                    info.autoDetected().stream().sorted().toList(), info.sheetName(),
                    info.format() == DataFormat.XLSX ? sheetDtos(info.sheets()) : null, columns, info.rowCount(),
                    info.blankRowsSkipped(), preview.limit(),
                    preview.rows().stream().map(DatasetPreviewDto::row).toList());
        }

        private static RowDto row(Row row) {
            return new RowDto(row.rowNumber(), row.values());
        }
    }

    private static List<SheetDto> sheetDtos(List<SheetInfo> sheets) {
        return sheets == null ? null
                : sheets.stream().map(sheet -> new SheetDto(sheet.name(), sheet.visible())).toList();
    }
}
