package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadLimits;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.TextEncoding;
import com.universaldatatools.platform.dataset.DatasetDtos.DatasetDto;
import com.universaldatatools.platform.dataset.DatasetDtos.DatasetPreviewDto;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** {@code /api/datasets}: upload once, preview with any read options, delete (spec: dataset-api). */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final DatasetService service;

    public DatasetController(DatasetService service) {
        this.service = service;
    }

    @Operation(summary = "Upload a CSV, XLSX or JSON dataset",
            description = "Checks the extension, content and size (20MB by default). Only the type is checked here; "
                    + "the file is read with the chosen options by the preview. Errors: 415 FILE_UNSUPPORTED, "
                    + "413 FILE_TOO_LARGE, 422 FILE_EMPTY, 422 FILE_PARSE_ERROR.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DatasetDto> upload(@RequestPart("file") MultipartFile file) {
        Dataset dataset;
        try (InputStream in = file.getInputStream()) {
            dataset = service.upload(file.getOriginalFilename(), in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ResponseEntity.created(URI.create("/api/datasets/" + dataset.id()))
                .cacheControl(CacheControl.noStore())
                .body(DatasetDto.of(dataset, service.expiresAt(dataset)));
    }

    @Operation(summary = "Read a dataset", description = "Errors: 404 DATASET_NOT_FOUND.")
    @GetMapping("/{id}")
    public ResponseEntity<DatasetDto> get(@PathVariable UUID id) {
        Dataset dataset = service.get(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(DatasetDto.of(dataset, service.expiresAt(dataset)));
    }

    @Operation(summary = "Preview a dataset read with the given options",
            description = "Reads the whole file with the options (empty ones are detected) and returns its columns, "
                    + "profiles and first rows. Errors: 404 DATASET_NOT_FOUND, 422 FILE_PARSE_ERROR, FILE_EMPTY, "
                    + "JSON_NOT_FLAT, LIMIT_EXCEEDED, CONFIG_INVALID (unknown sheet), 400 REQUEST_INVALID.")
    @GetMapping("/{id}/preview")
    public ResponseEntity<DatasetPreviewDto> preview(@PathVariable UUID id,
                                                     @RequestParam(required = false) Integer limit,
                                                     @RequestParam(required = false) String sheet,
                                                     @RequestParam(required = false) String delimiter,
                                                     @RequestParam(required = false) String encoding,
                                                     @RequestParam(required = false) Boolean hasHeader) {
        int rows = limit == null ? DEFAULT_LIMIT : limit;
        if (rows < 1 || rows > MAX_LIMIT) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "limit must be between 1 and " + MAX_LIMIT + ".");
        }
        ReadOptions options = new ReadOptions(sheet, delimiter(delimiter), encoding(encoding), hasHeader,
                ReadLimits.NONE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(DatasetPreviewDto.of(service.preview(id, options, rows)));
    }

    @Operation(summary = "Delete a dataset now",
            description = "Errors: 404 DATASET_NOT_FOUND, 503 SERVER_BUSY while it is being read.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code COMMA}, {@code comma}…; {@code null} when absent. */
    public static Delimiter delimiter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Delimiter.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.REQUEST_INVALID,
                    "delimiter must be one of " + Arrays.toString(Delimiter.values()) + ".");
        }
    }

    /** {@code UTF-8}, {@code windows-1258}…, in any case; {@code null} when absent. */
    public static TextEncoding encoding(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(TextEncoding.values())
                .filter(encoding -> encoding.apiName().equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.REQUEST_INVALID,
                        "encoding must be one of UTF-8, UTF-16, WINDOWS-1258, WINDOWS-1252."));
    }
}
