package com.universalimporter.domain.importsession;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
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

    /**
     * Every session that has something stored; anything not named after a session, and any link, is left out and
     * never touched.
     */
    List<StoredEntry> listEntries();

    /** The installation that claimed this storage, if any. */
    Optional<UUID> owner();

    /** Marks this storage as belonging to {@code installation}. */
    void claim(UUID installation);
}
