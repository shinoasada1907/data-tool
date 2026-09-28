package com.universaldatatools.tools.importer.api.importsession;

import com.universaldatatools.tools.importer.application.importsession.ImportSessionService;
import com.universaldatatools.tools.importer.application.importsession.SessionDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Import sessions", description = "Upload a source file and follow its import session")
@RestController
@RequestMapping("/api/import-sessions")
public class ImportSessionController {

    private final ImportSessionService service;

    public ImportSessionController(ImportSessionService service) {
        this.service = service;
    }

    @Operation(summary = "Upload a CSV or XLSX file",
            description = "Checks the type (extension and content) and size (20MB by default), reads the whole "
                    + "file once and creates an import session in CONFIGURING, with an empty target schema. "
                    + "Errors: 415 FILE_UNSUPPORTED, 413 FILE_TOO_LARGE, 422 FILE_EMPTY or FILE_PARSE_ERROR.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportSessionDto> upload(@RequestPart("file") MultipartFile file) {
        SessionDetails details = service.upload(file.getOriginalFilename(), file);
        return ResponseEntity.created(URI.create("/api/import-sessions/" + details.session().id()))
                .body(ImportSessionDto.from(details));
    }

    @Operation(summary = "Read an import session",
            description = "Status, file metadata, configuration and readiness of the session. "
                    + "Errors: 404 SESSION_NOT_FOUND.")
    @GetMapping("/{id}")
    ImportSessionDto get(@PathVariable UUID id) {
        return ImportSessionDto.from(service.details(id));
    }
}
