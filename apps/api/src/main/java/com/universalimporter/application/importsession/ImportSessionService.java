package com.universalimporter.application.importsession;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.importsession.FileStorage;
import com.universalimporter.domain.importsession.FileTypeDetector;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.OriginalFileName;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ImportSessionService {

    private static final Logger log = LoggerFactory.getLogger(ImportSessionService.class);

    private final ImportSessionRepository repository;
    private final FileStorage storage;
    private final Clock clock;

    public ImportSessionService(ImportSessionRepository repository, FileStorage storage, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    /** Validates the file, stores it under a new session id and creates the session (spec: import-session). */
    public ImportSession upload(String originalFileName, InputStreamSource content) {
        String name = OriginalFileName.sanitize(originalFileName);
        SourceFileType type = FileTypeDetector.detect(name, readHead(content));
        UUID id = UUID.randomUUID();
        long size = store(id, content);
        ImportSession session;
        try {
            session = repository.save(ImportSession.create(id, new SourceFile(name, type, size), now()));
        } catch (RuntimeException e) {
            storage.delete(id);
            throw e;
        }
        // Metadata only: the file content never goes to the log.
        log.info("Created import session {} ({}, {} bytes)", id, type, size);
        return session;
    }

    public ImportSession get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
    }

    private static byte[] readHead(InputStreamSource content) {
        try (InputStream in = content.getInputStream()) {
            return in.readNBytes(FileTypeDetector.HEAD_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the uploaded file", e);
        }
    }

    private long store(UUID id, InputStreamSource content) {
        try (InputStream in = content.getInputStream()) {
            return storage.save(id, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the uploaded file", e);
        }
    }

    /** PostgreSQL keeps microseconds; truncating here keeps memory and database in agreement. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
