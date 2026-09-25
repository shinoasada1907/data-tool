package com.universalimporter.api.importsession;

import com.universalimporter.application.importsession.SourcePreviewService;
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
@RestController
@RequestMapping("/api/import-sessions")
public class SourcePreviewController {

    private final SourcePreviewService service;

    public SourcePreviewController(SourcePreviewService service) {
        this.service = service;
    }

    @GetMapping("/{id}/preview")
    SourcePreviewDto preview(@PathVariable UUID id,
                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return SourcePreviewDto.from(service.preview(id, limit));
    }
}
