package com.universaldatatools.platform.storage;

import java.util.UUID;

/** The lasting id of this installation's database (BE-F11): what a storage folder is claimed by. */
@FunctionalInterface
public interface InstallationRepository {

    UUID installationId();
}
