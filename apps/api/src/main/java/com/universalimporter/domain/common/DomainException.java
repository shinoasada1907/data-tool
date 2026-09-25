package com.universalimporter.domain.common;

import java.util.List;
import java.util.Objects;

/** A business rule was violated; the api layer turns it into a ProblemDetail carrying {@link #code()}. */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final List<ProblemItem> items;

    public DomainException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public DomainException(ErrorCode code, String message, List<ProblemItem> items) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.items = List.copyOf(items);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ProblemItem> items() {
        return items;
    }
}
