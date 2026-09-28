package com.universaldatatools.tools.importer.domain.config;

/** Codes of the problems that keep a session from being ready to process; the name is the API value. */
public enum ReadinessIssueCode {
    SCHEMA_EMPTY,
    TARGET_FIELD_REQUIRED
}
