package com.universaldatatools.tools.importer.domain.config;

/**
 * Fingerprint of a configuration's content (design D7, S7): equal content gives an equal hash, whatever the
 * session or version.
 */
public interface ConfigHasher {

    String hash(ImportConfiguration configuration);
}
