package com.universalimporter.infrastructure.export;

import com.universalimporter.domain.pipeline.RowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** What the exporters share. */
final class ExportStreams {

    /** Lets Excel read the CSV as UTF-8 (contract V0.1). */
    static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    static final String CSV_CONTENT_TYPE = "text/csv;charset=" + StandardCharsets.UTF_8.name();

    private static final Logger log = LoggerFactory.getLogger(ExportStreams.class);

    private ExportStreams() {
    }

    /**
     * Last guard of "invalid rows never reach the valid file" (F10-D2): the valid view never holds one unless the
     * pipeline is wrong, so one is skipped and logged by its number, never its values (D13).
     */
    static boolean isValid(RowResult row) {
        if (row.valid() && row.errors().isEmpty()) {
            return true;
        }
        log.warn("Row {} is not valid and is left out of the valid export", row.rowNumber());
        return false;
    }

    /** Flushes instead of closing: the servlet container owns the response stream. */
    static OutputStream keepOpen(OutputStream out) {
        return new FilterOutputStream(out) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }
}
