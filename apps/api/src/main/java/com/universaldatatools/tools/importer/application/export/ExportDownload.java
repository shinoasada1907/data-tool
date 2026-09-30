package com.universaldatatools.tools.importer.application.export;

/**
 * A download whose checks are done and whose rows are already open; only the bytes are left to write. Close it
 * whether or not the body was written: closing releases the open rows, may be repeated, and never throws.
 */
public record ExportDownload(String fileName, String contentType, ExportBody body, Runnable closer)
        implements AutoCloseable {

    @Override
    public void close() {
        closer.run();
    }
}
