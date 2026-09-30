package com.universaldatatools.core.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A business rule was violated; the api layer turns it into a ProblemDetail carrying {@link #code()}, the items as
 * {@code errors} and each extension as a top-level member, except {@link #RETRY_AFTER}, which becomes the
 * {@code Retry-After} header (core-04 PL1).
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    /** Extension holding the seconds to wait before retrying; sent as the {@code Retry-After} header only. */
    public static final String RETRY_AFTER = "retryAfterSeconds";

    private final List<ProblemItem> items;
    private final Map<String, Object> extensions;

    public DomainException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public DomainException(ErrorCode code, String message, List<ProblemItem> items) {
        this(code, message, items, Map.of());
    }

    public DomainException(ErrorCode code, String message, List<ProblemItem> items, Map<String, Object> extensions) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.items = List.copyOf(items);
        this.extensions = Map.copyOf(extensions);
    }

    /** A refusal the client may retry after {@code seconds}, such as {@code SERVER_BUSY}. */
    public static DomainException retryLater(ErrorCode code, String message, int seconds) {
        return new DomainException(code, message, List.of(), Map.of(RETRY_AFTER, seconds));
    }

    public ErrorCode code() {
        return code;
    }

    public List<ProblemItem> items() {
        return items;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }
}
