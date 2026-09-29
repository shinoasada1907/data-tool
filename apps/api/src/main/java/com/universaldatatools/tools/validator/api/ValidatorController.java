package com.universaldatatools.tools.validator.api;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.platform.output.Downloads;
import com.universaldatatools.tools.validator.api.ValidatorDtos.CreateRunRequest;
import com.universaldatatools.tools.validator.api.ValidatorDtos.ExportRequest;
import com.universaldatatools.tools.validator.api.ValidatorDtos.ValidatorRowsDto;
import com.universaldatatools.tools.validator.api.ValidatorDtos.ValidatorRunDto;
import com.universaldatatools.tools.validator.application.ValidatorExports;
import com.universaldatatools.tools.validator.application.ValidatorRows;
import com.universaldatatools.tools.validator.application.ValidatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/** {@code /api/validator}: check a dataset against a schema, then page and export the result (spec: data-validator). */
@Tag(name = "Validator", description = "Validate a dataset against a schema with constraints")
@RestController
@RequestMapping("/api/validator/runs")
public class ValidatorController {

    private final ValidatorService service;
    private final ValidatorRows rows;
    private final ValidatorExports exports;

    public ValidatorController(ValidatorService service, ValidatorRows rows, ValidatorExports exports) {
        this.service = service;
        this.rows = rows;
        this.exports = exports;
    }

    @Operation(summary = "Validate a dataset",
            description = "Checks the schema first (422 SCHEMA_INVALID, each item with a pointer under /schema), then "
                    + "reads the dataset with source.options, matches fields to columns (same name, else trimmed and "
                    + "case-insensitive; 422 SCHEMA_INCOMPATIBLE with FIELD_MISSING items when a required field has "
                    + "none) and validates every row; unique counts every row. Other errors are the dataset's: "
                    + "404 DATASET_NOT_FOUND, 422 FILE_PARSE_ERROR, FILE_EMPTY, JSON_NOT_FLAT, LIMIT_EXCEEDED, "
                    + "CONFIG_INVALID, 400 REQUEST_INVALID.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidatorRunDto> create(@RequestBody CreateRunRequest request) {
        if (request.source() == null || request.schema() == null) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "source and schema are required.");
        }
        ValidatorService.ValidatorRun run = service.create(request.source().toRef("source"), request.schema().toSpec());
        return ResponseEntity.created(URI.create("/api/validator/runs/" + run.record().id()))
                .cacheControl(CacheControl.noStore())
                .body(ValidatorRunDto.of(run));
    }

    @Operation(summary = "Get a validation run", description = "404 RUN_NOT_FOUND when missing or expired.")
    @GetMapping("/{id}")
    public ResponseEntity<ValidatorRunDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ValidatorRunDto.of(service.get(id)));
    }

    @Operation(summary = "Rows of a validation run",
            description = "view: VALID (default) or INVALID; field and code keep INVALID rows with a matching error; "
                    + "page from 0; size 1-200 (default 50). values follow the run's fields; errors[].value is the "
                    + "source cell. 400 REQUEST_INVALID, 404 RUN_NOT_FOUND.")
    @GetMapping("/{id}/rows")
    public ResponseEntity<ValidatorRowsDto> rows(@PathVariable UUID id,
                                                 @RequestParam(required = false) String view,
                                                 @RequestParam(required = false) String field,
                                                 @RequestParam(required = false) String code,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ValidatorRowsDto.of(rows.page(id, view, field, code, page, size)));
    }

    @Operation(summary = "Export a validation run",
            description = "content VALID (the schema's fields, typed by the schema), INVALID (_row, the fields, "
                    + "_errors) or ERRORS (row, field, code, rule, message, value); output as for the Converter. "
                    + "Every error comes before the first byte: 400 REQUEST_INVALID, 404 RUN_NOT_FOUND, "
                    + "422 CONFIG_INVALID, LIMIT_EXCEEDED (XLSX too small).")
    @PostMapping(path = "/{id}/export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void export(@PathVariable UUID id, @RequestBody ExportRequest request, HttpServletResponse response)
            throws IOException {
        if (request.output() == null) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "content and output are required.");
        }
        ValidatorExports.Prepared prepared = exports.prepare(id, request.content(), request.output());
        Downloads.send(response, prepared.fileName(), prepared.contentType(), prepared.body(),
                "export of validator run " + id);
    }

    @Operation(summary = "Delete a validation run", description = "404 RUN_NOT_FOUND when missing or expired.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
