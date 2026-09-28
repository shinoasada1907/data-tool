package com.universaldatatools.platform.storage;

import java.time.Instant;
import java.util.UUID;

/** What the storage holds for one session, as its cleanup sees it (BE-F11 D2). */
public record StoredEntry(UUID sessionId, Instant lastModified) {
}
