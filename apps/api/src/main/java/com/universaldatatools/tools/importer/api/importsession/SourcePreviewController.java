package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.tools.importer.application.importsession.SourcePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// No class-level @Validated on purpose: Spring MVC validates the constraints below itself and reports a
// HandlerMethodValidationException (400 REQUEST_INVALID). @Validated would switch to AOP validation, whose
// ConstraintViolationException the error handler treats as an unexpected 500.
@Tag(name = "Import sessions")
@RestController
@RequestMapping("/api/import-sessions")
public class SourcePreviewController {

    private final SourcePreviewService service;

    public SourcePreviewController(SourcePreviewService service) {
        this.service = service;
    }

    @Operation(summary = "Preview the source file",
            description = "Columns in file order, total data rows, and the first rows with their spreadsheet row "
                    + "numbers (header is row 1). Errors: 400 REQUEST_INVALID (limit outside 1-200), "
                    + "404 SESSION_NOT_FOUND, 409 SESSION_STATE_INVALID (file not read yet).")
    @GetMapping("/{id}/preview")
    SourcePreviewDto preview(@PathVariable UUID id,
                             @Parameter(description = "How many data rows to return (1-200)")
                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return SourcePreviewDto.from(service.preview(id, limit));
    }
}
