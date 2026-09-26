package com.universalimporter.application.export;

/** A download whose checks are done and whose rows are already open; only the bytes are left to write. */
public record ExportDownload(String fileName, String contentType, ExportBody body) {
}
