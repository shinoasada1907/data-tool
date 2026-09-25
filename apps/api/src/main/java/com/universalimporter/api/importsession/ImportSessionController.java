package com.universalimporter.api.importsession;

import com.universalimporter.application.importsession.ImportSessionService;
import com.universalimporter.domain.importsession.ImportSession;
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

@RestController
@RequestMapping("/api/import-sessions")
public class ImportSessionController {

    private final ImportSessionService service;

    public ImportSessionController(ImportSessionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportSessionDto> upload(@RequestPart("file") MultipartFile file) {
        ImportSession session = service.upload(file.getOriginalFilename(), file);
        return ResponseEntity.created(URI.create("/api/import-sessions/" + session.id()))
                .body(ImportSessionDto.from(session));
    }

    @GetMapping("/{id}")
    ImportSessionDto get(@PathVariable UUID id) {
        return ImportSessionDto.from(service.get(id));
    }
}
