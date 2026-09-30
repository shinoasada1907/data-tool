package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What the exporters share. */
final class ExportStreams {

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
}
