package com.googlecode.download.maven.plugin.internal.cache;

import java.net.URI;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Convenient map to search for the path where file is locally stored
 * by uri of the resource the file was downloaded from.
 * Implementations should not read/write file bodies using stored paths.
 *
 * @author Paul Polishchuk
 * @since 1.3.1
 */
interface FileIndex {

    /**
     * Adds given path to the index using uri parameter as a key.
     * @param uri index key
     * @param path index value
     */
    void put(URI uri, String path);

    /**
     * Gets stored value by the key.
     * @param uri index key
     * @return path by given uri key; {@literal null} if not found.
     */
    String get(URI uri);

    /**
     * The lock to be used when accessing the index.
     * @return The lock.
     */
    ReentrantLock getLock();
}
