package com.universalimporter.api.validation;

import com.universalimporter.api.importsession.ConfigUpdateResponseDto;
import com.universalimporter.application.configuration.ConfigurationService;
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
public class ValidationConfigController {

    private final ConfigurationService service;

    public ValidationConfigController(ConfigurationService service) {
        this.service = service;
    }

    @Operation(summary = "Replace the validation rules",
            description = "Rules {targetField, type, params?}: email (string fields only) and unique. required "
                    + "and type come from the schema: sending them, or email on an email field, is not an error but "
                    + "comes back as a RULE_IMPLIED_BY_SCHEMA warning and is not stored. Replaces the whole list; "
                    + "rules come back in schema order, email before unique. Errors: 400 REQUEST_INVALID "
                    + "(malformed body), 404 SESSION_NOT_FOUND, 409 SESSION_STATE_INVALID, 422 CONFIG_INVALID "
                    + "(every problem listed in errors).")
    @PutMapping("/{id}/validations")
    ConfigUpdateResponseDto updateValidations(@PathVariable UUID id,
                                              @Valid @RequestBody ValidationConfigDto validations) {
        return ConfigUpdateResponseDto.from(service.updateValidations(id, validations.toDomain()));
    }
}
