package com.universaldatatools.tools.importer.api.mapping;

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
public class MappingController {

    private final ConfigurationService service;

    public MappingController(ConfigurationService service) {
        this.service = service;
    }

    @Operation(summary = "Replace the field mapping",
            description = "Lists only mapped fields; each takes its value from a source column (exact name from "
                    + "the preview) or a constant. Replaces the whole mapping; mappings come back in schema order. "
                    + "Every unmapped field is reported in warnings as TARGET_FIELD_UNMAPPED; the session is READY "
                    + "only when every required field is mapped. Errors: 400 REQUEST_INVALID (malformed body), "
                    + "404 SESSION_NOT_FOUND, 409 SESSION_STATE_INVALID, 422 MAPPING_INVALID or "
                    + "SOURCE_COLUMN_NOT_FOUND (every problem listed in errors, field = the target field).")
    @PutMapping("/{id}/mapping")
    ConfigUpdateResponseDto updateMapping(@PathVariable UUID id, @Valid @RequestBody MappingConfigDto mapping) {
        return ConfigUpdateResponseDto.from(service.updateMapping(id, mapping.toSpecs()));
    }
}
