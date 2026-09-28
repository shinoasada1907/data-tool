package com.universaldatatools.tools.importer.domain.importsession;

/** Lifecycle of an import session (design D2). */
public enum SessionStatus {
    UPLOADED, CONFIGURING, READY, PROCESSED, FAILED;

    public boolean canTransitionTo(SessionStatus target) {
        return switch (this) {
            case UPLOADED -> target == CONFIGURING;
            case CONFIGURING -> target == CONFIGURING || target == READY;
            // Re-saving config or re-processing keeps a session where it is.
            case READY, PROCESSED -> target != UPLOADED;
            case FAILED -> false;
        };
    }
}
