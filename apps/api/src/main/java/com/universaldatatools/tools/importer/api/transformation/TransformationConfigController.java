package com.universaldatatools.tools.importer.api.transformation;

import com.universaldatatools.tools.importer.api.importsession.ConfigUpdateResponseDto;
import com.universaldatatools.tools.importer.application.configuration.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Configuration")
@RestController
@RequestMapping("/api/import-sessions")
public class TransformationConfigController {

    private final ConfigurationService service;

    public TransformationConfigController(ConfigurationService service) {
        this.service = service;
    }

    @Operation(summary = "Replace the transformations",
            description = "A flat list of steps {targetField, order, type, params?}; each field's steps run by "
                    + "order. Types: trim, uppercase, lowercase (no params), defaultValue {value}, dateFormat "
                    + "{inputFormat, outputFormat? = yyyy-MM-dd}; a date field must output yyyy-MM-dd. Replaces the "
                    + "whole list; an empty list removes every step. Steps come back in schema order, then by "
                    + "order. Errors: 400 REQUEST_INVALID (malformed body), 404 SESSION_NOT_FOUND, "
                    + "409 SESSION_STATE_INVALID, 422 CONFIG_INVALID (every problem listed in errors).")
    @PutMapping("/{id}/transformations")
    ConfigUpdateResponseDto updateTransformations(@PathVariable UUID id,
                                                  @Valid @RequestBody TransformationConfigDto transformations) {
        return ConfigUpdateResponseDto.from(service.updateTransformations(id, transformations.toDomain()));
    }
}
