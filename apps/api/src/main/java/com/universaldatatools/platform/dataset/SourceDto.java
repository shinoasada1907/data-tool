package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadLimits;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.TextEncoding;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code SourceDto} of the API contract Toolbox v1: a dataset and the options to read it with, as every tool
 * request carries it. Enum values are text in any case; a wrong one is {@code CONFIG_INVALID}.
 */
public record SourceDto(UUID datasetId, Options options) {

    public record Options(String sheet, String delimiter, String encoding, Boolean hasHeader) {
    }

    /**
     * @param field where this source sits in the request body, such as {@code source} or {@code old}
     * @throws DomainException {@code REQUEST_INVALID} without a dataset id, {@code CONFIG_INVALID} for a wrong
     *                         delimiter or encoding
     */
    public SourceRef toRef(String field) {
        if (datasetId == null) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, field + ".datasetId is required.");
        }
        if (options == null) {
            return new SourceRef(datasetId, ReadOptions.defaults());
        }
        return new SourceRef(datasetId, new ReadOptions(options.sheet(),
                delimiter(options.delimiter(), field + ".options.delimiter"),
                encoding(options.encoding(), field + ".options.encoding"), options.hasHeader(), ReadLimits.NONE));
    }

    private static Delimiter delimiter(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Delimiter.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid(field + " must be one of " + Arrays.toString(Delimiter.values()) + ".");
        }
    }

    private static TextEncoding encoding(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(TextEncoding.values())
                .filter(encoding -> encoding.apiName().equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElseThrow(() -> invalid(field + " must be one of UTF-8, UTF-16, WINDOWS-1258, WINDOWS-1252."));
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.CONFIG_INVALID, "Source options are invalid.",
                List.of(new ProblemItem(null, ErrorCode.CONFIG_INVALID.name(), message)));
    }
}
