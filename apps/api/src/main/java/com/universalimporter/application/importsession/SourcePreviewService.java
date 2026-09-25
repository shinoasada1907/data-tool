package com.universalimporter.application.importsession;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.importsession.FileStorage;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.source.ImportRow;
import com.universalimporter.domain.source.SourceParser;
import com.universalimporter.domain.source.SourceSchema;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** First rows of the source file (spec: source-parsing, design P8). Read-only: allowed in every inspected state. */
@Service
public class SourcePreviewService {

    private final ImportSessionRepository sessions;
    private final FileStorage storage;
    private final SourceParsers parsers;

    public SourcePreviewService(ImportSessionRepository sessions, FileStorage storage, SourceParsers parsers) {
        this.sessions = sessions;
        this.storage = storage;
        this.parsers = parsers;
    }

    public SourcePreview preview(UUID sessionId, int limit) {
        ImportSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
        SourceSchema schema = session.sourceSchema()
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_STATE_INVALID,
                        "Source file has not been inspected."));
        SourceFileType type = session.sourceFile().fileType();
        // Inspected at upload, so a parser existed then; its absence now is a deployment bug, not a client error.
        SourceParser parser = parsers.find(type)
                .orElseThrow(() -> new IllegalStateException("No parser for inspected file type " + type));
        List<ImportRow> rows;
        try (InputStream in = storage.open(sessionId); Stream<ImportRow> all = parser.read(in)) {
            rows = all.limit(limit).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the stored file", e);
        }
        return new SourcePreview(sessionId, type, schema.sheetName(), schema.columns(), rows, limit,
                schema.totalRows());
    }
}
