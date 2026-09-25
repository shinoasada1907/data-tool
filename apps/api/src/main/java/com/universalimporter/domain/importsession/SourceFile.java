package com.universalimporter.domain.importsession;

/**
 * Metadata of the uploaded file. The content itself lives in {@link FileStorage}, keyed by session id.
 *
 * @param originalFileName sanitized client file name, kept for display only (never used as a path)
 */
public record SourceFile(String originalFileName, SourceFileType fileType, long sizeBytes) {
}
