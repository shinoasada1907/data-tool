package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.SheetInfo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An uploaded dataset (core-04 PL5): the file sits at {@code {storage}/{id}/source.bin}.
 *
 * @param originalFileName sanitized client name, metadata only
 * @param sheets           the workbook's sheets for XLSX, {@code null} otherwise
 */
public record Dataset(UUID id, String originalFileName, DataFormat format, long sizeBytes, List<SheetInfo> sheets,
                      Instant createdAt, Instant lastUsedAt) {

    public Dataset {
        sheets = sheets == null ? null : List.copyOf(sheets);
    }
}
