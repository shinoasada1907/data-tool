package com.universalimporter.api.export;

import com.universalimporter.application.export.ExportDownload;
import com.universalimporter.application.export.ExportService;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.export.ExportFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Streams downloads synchronously, straight into the servlet response (design F10-D1, changed after review).
 * Not {@code StreamingResponseBody}: its async request can time out mid-download and end as a clean, truncated
 * 200; its task can be cancelled before it runs, leaving the rows open; and it holds one of a few executor
 * threads per download. A synchronous write has none of these: the container's socket timeout ends a stalled
 * client, and the rows are closed on every path.
 */
@Tag(name = "Export", description = "Download the valid rows and the error report")
@RestController
@RequestMapping("/api/import-sessions")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @Operation(summary = "Download the valid rows",
            description = "format=json: an array of objects, keys in schema order, typed values. format=csv: UTF-8 "
                    + "with BOM, RFC 4180, header in schema order, text cells guarded against formulas. Only valid "
                    + "rows. Errors: 400 REQUEST_INVALID (format missing or not json/csv), 404 SESSION_NOT_FOUND, "
                    + "409 RESULT_NOT_AVAILABLE (as GET /result), 500 EXPORT_FAILED. An error after the file has "
                    + "started drops the connection, so the download fails instead of ending short.")
    @GetMapping("/{id}/export")
    void export(@PathVariable UUID id, @RequestParam(required = false) String format, HttpServletResponse response)
            throws IOException {
        ExportFormat parsed = ExportFormat.parse(format);
        try (ExportDownload download = service.prepareValidRows(id, parsed)) {
            send(id, download, response);
        }
    }

    @Operation(summary = "Download the error report",
            description = "CSV, UTF-8 with BOM: rowNumber,fieldName,stage,rule,step,code,message,sourceValue, one "
                    + "line per error. Errors: 404 SESSION_NOT_FOUND, 409 RESULT_NOT_AVAILABLE, 500 EXPORT_FAILED.")
    @GetMapping("/{id}/errors/export")
    void errorReport(@PathVariable UUID id, HttpServletResponse response) throws IOException {
        try (ExportDownload download = service.prepareErrorReport(id)) {
            send(id, download, response);
        }
    }

    /**
     * A failure while nothing has reached the client yet is still a clean {@code 500 EXPORT_FAILED}: the response
     * is reset, headers included. Once bytes are out, the status cannot change: the exception goes on to the
     * container, which drops the connection ({@code GlobalExceptionHandler} and
     * {@code CommittedErrorPageFilter} keep any error body out of the file).
     */
    private static void send(UUID id, ExportDownload download, HttpServletResponse response) throws IOException {
        response.setContentType(download.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString());
        try {
            download.body().writeTo(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException | RuntimeException e) {
            if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
                log.debug("Client left during the export of session {}", id);
                throw e;
            }
            log.error("Export stream failed for session {}", id, e);
            if (!response.isCommitted()) {
                response.reset();
                throw new DomainException(ErrorCode.EXPORT_FAILED, "Export failed.");
            }
            throw e;
        }
    }
}
