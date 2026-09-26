package com.universalimporter.api.schema;

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

@Tag(name = "Configuration", description = "Target schema, mapping, transformations and validations of a session")
@RestController
@RequestMapping("/api/import-sessions")
public class SchemaController {

    private final ConfigurationService service;

    public SchemaController(ConfigurationService service) {
        this.service = service;
    }

    @Operation(summary = "Replace the target schema",
            description = "Replaces the whole schema. Names are trimmed; fields come back sorted by order and "
                    + "renumbered from 0. Configuration of fields that no longer exist is removed and reported "
                    + "in warnings. The session moves to READY when nothing else is missing, otherwise to "
                    + "CONFIGURING. Errors: 400 REQUEST_INVALID (malformed body), 404 SESSION_NOT_FOUND, "
                    + "409 SESSION_STATE_INVALID (file not read yet, or session failed), 422 SCHEMA_INVALID "
                    + "(every problem listed in errors, field = the trimmed name or null).")
    @PutMapping("/{id}/schema")
    ConfigUpdateResponseDto updateSchema(@PathVariable UUID id, @Valid @RequestBody TargetSchemaDto schema) {
        return ConfigUpdateResponseDto.from(service.updateSchema(id, schema.toSpecs()));
    }
}
