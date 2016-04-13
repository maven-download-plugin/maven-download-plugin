package com.googlecode.download.maven.plugin.internal.cache;

import java.net.URL;

/**
 * Convenient map to search for the path where file is locally stored
 * by url of the resource the file was downloaded from.
 * Implementations should not read/write file bodies using stored paths.
 *
 * @author Paul Polishchuk
 * @since 1.3.1
 */
interface FileIndex {

    /**
     * Adds given path to the index using url parameter as a key.
     * @param url index key
     * @param path index value
     */
    void put(URL url, String path);

    /**
     * Check if a path associated with the url key in the index.
     * <p>Use this method before actually trying to get value.
     * @param url index key
     * @return true if some path associated with given key
     */
    boolean contains(URL url);

    /**
     * Gets stored value by the key.
     * @param url index key
     * @return path by given url key; never NULL
     * @throws IllegalStateException in case key is not found
     */
    String get(URL url);
}
