package com.universalimporter.application.export;

import com.universalimporter.application.result.CurrentResult;
import com.universalimporter.application.result.ResultQueryService;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.export.ErrorReportExporter;
import com.universalimporter.domain.export.ExportFileName;
import com.universalimporter.domain.export.ExportFormat;
import com.universalimporter.domain.export.ValidRowsExporter;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.pipeline.ResultView;
import com.universalimporter.domain.schema.TargetField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prepares downloads of a processed result (design F10-D1): every check, and the opening of the rows, happens
 * before the first byte, so a problem is still an ordinary error response. The rows are the detached stream of
 * {@link ResultQueryService#openCurrent}: writing them holds no lock and is not disturbed by a later run or
 * configuration change.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ImportSessionRepository sessions;
    private final ResultQueryService results;
    private final Map<ExportFormat, ValidRowsExporter> validExporters = new EnumMap<>(ExportFormat.class);
    private final ErrorReportExporter errorExporter;

    public ExportService(ImportSessionRepository sessions, ResultQueryService results,
                         List<ValidRowsExporter> validExporters, ErrorReportExporter errorExporter) {
        this.sessions = sessions;
        this.results = results;
        validExporters.forEach(exporter -> this.validExporters.put(exporter.format(), exporter));
        this.errorExporter = errorExporter;
    }

    /**
     * @throws DomainException {@code SESSION_NOT_FOUND}, {@code RESULT_NOT_AVAILABLE} (as {@code GET /result}),
     *                         or {@code EXPORT_FAILED} when the rows cannot be opened
     */
    public ExportDownload prepareValidRows(UUID sessionId, ExportFormat format) {
        ValidRowsExporter exporter = validExporters.get(format);
        if (exporter == null) {
            throw new IllegalStateException("No exporter for " + format);
        }
        String fileName = fileName(sessionId, exporter.fileSuffix());
        CurrentResult current = open(sessionId, ResultView.VALID);
        List<TargetField> fields = current.configuration().schema().fields();
        return new ExportDownload(fileName, exporter.contentType(), out -> {
            try (current) {
                exporter.write(fields, current.rows(), out);
            }
        });
    }

    /** As {@link #prepareValidRows}, over the invalid rows. */
    public ExportDownload prepareErrorReport(UUID sessionId) {
        String fileName = fileName(sessionId, errorExporter.fileSuffix());
        CurrentResult current = open(sessionId, ResultView.INVALID);
        return new ExportDownload(fileName, errorExporter.contentType(), out -> {
            try (current) {
                errorExporter.write(current.rows(), out);
            }
        });
    }

    private String fileName(UUID sessionId, String suffix) {
        ImportSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
        return ExportFileName.of(session.sourceFile().originalFileName(), suffix);
    }

    private CurrentResult open(UUID sessionId, ResultView view) {
        try {
            return results.openCurrent(sessionId, view);
        } catch (UncheckedIOException e) {
            log.error("Export of session {} could not be started", sessionId, e);
            throw new DomainException(ErrorCode.EXPORT_FAILED, "Export could not be started.");
        }
    }
}
