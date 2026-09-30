package com.universaldatatools.tools.converter.api;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.platform.dataset.SourceDto;
import com.universaldatatools.platform.output.Downloads;
import com.universaldatatools.platform.output.OutputDto;
import com.universaldatatools.tools.converter.application.ConverterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** {@code /api/converter}: a dataset to CSV, XLSX or JSON (spec: data-converter). */
@Tag(name = "Converter", description = "Convert a dataset between CSV, XLSX and JSON")
@RestController
@RequestMapping("/api/converter")
public class ConverterController {

    private final ConverterService service;

    public ConverterController(ConverterService service) {
        this.service = service;
    }

    /** {@code ConvertRequest} of the API contract Toolbox v1. */
    public record ConvertRequest(SourceDto source, OutputDto output) {
    }

    @Operation(summary = "Convert a dataset",
            description = "Reads the dataset with source.options (as its preview does) and streams it in "
                    + "output.format: CSV (delimiter, header, BOM, formula guard), JSON (pretty, typing) or XLSX "
                    + "(sheet name, typing). typing PRESERVE keeps each cell's source type. Every error comes before "
                    + "the first byte: 404 DATASET_NOT_FOUND, 422 FILE_PARSE_ERROR, FILE_EMPTY, JSON_NOT_FLAT, "
                    + "LIMIT_EXCEEDED (also when XLSX cannot hold the dataset), CONFIG_INVALID, 400 REQUEST_INVALID.")
    @PostMapping(path = "/convert", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void convert(@RequestBody ConvertRequest request, HttpServletResponse response) throws IOException {
        if (request.source() == null || request.output() == null) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "source and output are required.");
        }
        try (ConverterService.Prepared prepared = service.prepare(request.source().toRef("source"),
                request.output())) {
            Downloads.send(response, prepared.fileName(), prepared.contentType(), prepared.body(),
                    "conversion of dataset " + request.source().datasetId());
        }
    }
}
