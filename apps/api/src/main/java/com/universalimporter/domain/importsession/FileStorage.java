package com.universalimporter.domain.importsession;

import java.io.InputStream;
import java.util.UUID;

/**
 * Stores the uploaded file of a session (design D6). The session id is the only key: no client-supplied
 * name ever reaches the storage location. I/O failures surface as {@link java.io.UncheckedIOException}.
 */
public interface FileStorage {

    /** Copies {@code content} (without closing it) and returns the number of bytes stored. */
    long save(UUID sessionId, InputStream content);

    /** Opens the stored file; the caller closes the stream. */
    InputStream open(UUID sessionId);

    /** Removes everything stored for the session; does nothing when there is nothing to remove. */
    void delete(UUID sessionId);
}
