package com.universaldatatools.platform.storage;

import java.util.UUID;

/**
 * A kind of resource that keeps files under the storage root, named by its id (core-01 TD17). The orphan sweep only
 * deletes a directory no owner claims, so one kind of resource never loses another's files.
 */
public interface StorageOwner {

    boolean owns(UUID id);
}
