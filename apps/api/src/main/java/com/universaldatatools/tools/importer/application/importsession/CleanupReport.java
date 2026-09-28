package com.universaldatatools.tools.importer.application.importsession;

/**
 * What one cleanup run did: counts only, no user data (D13).
 *
 * @param skipped   expired sessions in use at the time, left for the next run
 * @param failures  sessions or directories that could not be deleted, retried next run
 */
public record CleanupReport(int deletedSessions, int deletedOrphans, int skipped, int failures) {
}
