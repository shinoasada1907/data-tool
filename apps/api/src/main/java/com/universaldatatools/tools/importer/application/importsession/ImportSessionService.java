package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.OriginalFileName;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.FileTypeDetector;
import com.universaldatatools.core.table.SourceParser;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.platform.storage.FileStorage;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfigurationRepository;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.ImportSessionRepository;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImportSessionService {

    private static final Logger log = LoggerFactory.getLogger(ImportSessionService.class);

    private final ImportSessionRepository repository;
    private final ImportConfigurationRepository configurations;
    private final FileStorage storage;
    private final Clock clock;
    private final SourceParsers sourceParsers;

    public ImportSessionService(ImportSessionRepository repository, ImportConfigurationRepository configurations,
                                FileStorage storage, Clock clock, SourceParsers sourceParsers) {
        this.repository = repository;
        this.configurations = configurations;
        this.storage = storage;
        this.clock = clock;
        this.sourceParsers = sourceParsers;
    }

    /**
     * Validates the file, stores it under a new session id, reads it once when a parser exists for its type,
     * and creates the session (spec: import-session, source-parsing). On any failure nothing is left behind.
     * A new session has no configuration yet.
     */
    public SessionDetails upload(String originalFileName, InputStreamSource content) {
        String name = OriginalFileName.sanitize(originalFileName);
        DataFormat type = FileTypeDetector.detect(name, readHead(content));
        UUID id = UUID.randomUUID();
        long size = store(id, content);
        Instant now = now();
        ImportSession session = ImportSession.create(id, new SourceFile(name, type, size), now);
        try {
            Optional<SourceParser> parser = sourceParsers.find(type);
            if (parser.isPresent()) {
                session.markInspected(inspect(parser.get(), id), now);
            }
            session = repository.save(session);
        } catch (RuntimeException e) {
            storage.delete(id);
            throw e;
        }
        // Metadata only: the file content never goes to the log.
        log.info("Created import session {} ({}, {} bytes, {})", id, type, size, session.status());
        return SessionDetails.of(session, ImportConfiguration.empty(id));
    }

    /**
     * The session with its stored configuration; a session never configured has the empty one. Both are read
     * from one snapshot, so a PUT committing in between cannot pair an old status with a new schema.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SessionDetails details(UUID id) {
        ImportSession session = repository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
        ImportConfiguration configuration = configurations.findBySessionId(id)
                .orElseGet(() -> ImportConfiguration.empty(id));
        return SessionDetails.of(session, configuration);
    }

    /** Reads the stored copy, so what is inspected is exactly what later steps will read. */
    private SourceSchema inspect(SourceParser parser, UUID id) {
        try (InputStream in = storage.open(id)) {
            return parser.inspect(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the stored file", e);
        }
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
