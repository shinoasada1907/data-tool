package com.universalimporter.api.export;

import com.universalimporter.application.export.ExportDownload;
import com.universalimporter.application.export.ExportService;
import com.universalimporter.domain.export.ExportFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
                    + "started drops the connection.")
    @GetMapping("/{id}/export")
    ResponseEntity<StreamingResponseBody> export(@PathVariable UUID id,
                                                 @RequestParam(required = false) String format,
                                                 HttpServletResponse response) {
        return download(id, service.prepareValidRows(id, ExportFormat.parse(format)), response);
    }

    @Operation(summary = "Download the error report",
            description = "CSV, UTF-8 with BOM: rowNumber,fieldName,stage,rule,step,code,message,sourceValue, one "
                    + "line per error. Errors: 404 SESSION_NOT_FOUND, 409 RESULT_NOT_AVAILABLE, 500 EXPORT_FAILED.")
    @GetMapping("/{id}/errors/export")
    ResponseEntity<StreamingResponseBody> errorReport(@PathVariable UUID id, HttpServletResponse response) {
        return download(id, service.prepareErrorReport(id), response);
    }

    /**
     * Past this point the status is 200 and cannot change (design F10-D1). A failure while writing commits the
     * response and propagates, so the container drops the connection and the client sees a failed download,
     * never a short file or an error body inside it. The commit goes through the servlet response: the stream
     * Spring hands to the body ignores {@code flush()}.
     */
    private static ResponseEntity<StreamingResponseBody> download(UUID id, ExportDownload download,
                                                                 HttpServletResponse response) {
        StreamingResponseBody body = out -> {
            try {
                download.body().writeTo(out);
            } catch (IOException | RuntimeException e) {
                log.error("Export stream failed for session {}", id, e);
                try {
                    response.flushBuffer();
                } catch (IOException flush) {
                    e.addSuppressed(flush);
                }
                throw e;
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build()
                                .toString())
                .body(body);
    }
}
