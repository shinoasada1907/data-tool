package com.universaldatatools.tools.importer.api.process;

import com.universaldatatools.tools.importer.application.pipeline.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Processing", description = "Run the import and read its result")
@RestController
@RequestMapping("/api/import-sessions")
public class ProcessController {

    private final ProcessService service;

    public ProcessController(ProcessService service) {
        this.service = service;
    }

    @Operation(summary = "Run the import",
            description = "Maps, transforms and validates every row of the file, stores the result and moves the "
                    + "session to PROCESSED. Synchronous; calling it again re-runs and replaces the result. Errors: "
                    + "404 SESSION_NOT_FOUND, 409 SESSION_NOT_READY (readiness issues in errors), 409 "
                    + "SESSION_STATE_INVALID (session failed), 422 FILE_PARSE_ERROR (file broken since upload; fails "
                    + "the session for good), 500 INTERNAL_ERROR. A 500 fails the session only when the file itself "
                    + "cannot be read; when the result cannot be stored the session and its previous result are "
                    + "unchanged, so read the session again to tell.")
    @PostMapping("/{id}/process")
    PipelineSummaryDto process(@PathVariable UUID id) {
        return PipelineSummaryDto.from(service.process(id));
    }
}
