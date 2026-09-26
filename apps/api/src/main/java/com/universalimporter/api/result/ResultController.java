package com.universalimporter.api.result;

import com.universalimporter.application.result.ResultQuery;
import com.universalimporter.application.result.ResultQueryService;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.pipeline.ResultView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@Tag(name = "Processing", description = "Run the import and read its result")
@RestController
@RequestMapping("/api/import-sessions")
public class ResultController {

    static final int MAX_SIZE = 200;

    private final ResultQueryService service;

    public ResultController(ResultQueryService service) {
        this.service = service;
    }

    @Operation(summary = "Read the result a page at a time",
            description = "Valid or invalid rows by increasing row number, with the summary of the whole result. "
                    + "view: valid (default) or invalid; page from 0; size 1-200 (default 50). field and code keep "
                    + "the invalid rows having one error that matches both; they are ignored for valid rows. "
                    + "Errors: 400 REQUEST_INVALID, 404 SESSION_NOT_FOUND, 409 RESULT_NOT_AVAILABLE (not processed, "
                    + "or the configuration changed since).")
    @GetMapping("/{id}/result")
    PipelineResultDto result(@PathVariable UUID id,
                             @RequestParam(defaultValue = "valid") String view,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size,
                             @RequestParam(required = false) String field,
                             @RequestParam(required = false) String code) {
        if (page < 0) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "page must be 0 or more.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "size must be between 1 and " + MAX_SIZE + ".");
        }
        ResultQuery query = new ResultQuery(parseView(view), page, size, blankToNull(field), blankToNull(code));
        return PipelineResultDto.from(id, service.query(id, query));
    }

    private static ResultView parseView(String view) {
        return switch (view.toLowerCase(Locale.ROOT)) {
            case "valid" -> ResultView.VALID;
            case "invalid" -> ResultView.INVALID;
            default -> throw new DomainException(ErrorCode.REQUEST_INVALID, "view must be valid or invalid.");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
