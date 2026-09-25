package com.universalimporter.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** @param dir root directory for session files ({@code IMPORTER_STORAGE_DIR}) */
@ConfigurationProperties("importer.storage")
public record StorageProperties(Path dir) {
}
