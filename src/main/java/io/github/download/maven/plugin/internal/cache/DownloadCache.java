/*
 * Copyright 2009-2018 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.download.maven.plugin.internal.cache;

import io.github.download.maven.plugin.internal.checksum.Checksums;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

/**
 * A class representing a download cache.
 * @author Mickael Istria (Red Hat Inc)
 */
public final class DownloadCache {

    /**
     * Directory where the download cache is stored.
     */
    private final File basedir;

    /**
     * Index of cached files in the download cache, mapping URIs to local file paths.
     * This index enables fast retrieval of cached files and ensures thread-safe
     * access through locking mechanisms.
     */
    private final FileIndex index;

    /**
     * Constructor.
     * @param cacheDirectory Directory where the download cache is stored.
     * @param log Logger.
     */
    public DownloadCache(final File cacheDirectory, final Log log) {
        this.index = new FileBackedIndex(cacheDirectory, log);
        this.basedir = cacheDirectory;
    }

    /**
     * Get a File in the download cache. If no cache for this URL, or
     * if expected checksums don't match cached ones, returns null.
     * available in cache,
     * @param uri URL of the file
     * @param checksums Supplied checksums.
     * @return A File when cache is found, null if no available cache
     */
    public File getArtifact(final URI uri, final Checksums checksums) {
        final Optional<String> resource;
        this.index.getLock().lock();
        try {
            resource = this.getEntry(uri, checksums);
        } finally {
            this.index.getLock().unlock();
        }
        return resource.map(res -> new File(this.basedir, res)).orElse(null);
    }

    /**
     * Installs a file into the download cache.
     * If the cache directory does not exist, it is created.
     * The method checks if the file corresponding to the URI and checksums already exists in the
     * cache. If not, the file is copied to the cache directory and the index is updated.
     *
     * @param uri The URI of the file to be installed.
     * @param outputFile The file to be installed into the cache.
     * @param checksums The checksums used to verify the integrity of the file.
     * @throws MojoFailureException If the cache directory cannot be created.
     * @throws IOException If an I/O error occurs while copying the file.
     */
    public void install(final URI uri, final File outputFile, final Checksums checksums)
        throws MojoFailureException, IOException {
        if (!this.basedir.exists() && !this.basedir.mkdirs()) {
            throw new MojoFailureException(
                String.format(
                    "Could not create cache directory: %s", this.basedir.getAbsolutePath()
                )
            );
        }
        this.index.getLock().lock();
        try {
            final Optional<String> entry = this.getEntry(uri, checksums);
            if (!entry.isPresent()) {
                final String fileName = String.format(
                    "%s_%s", outputFile.getName(), DigestUtils.md5Hex(uri.toString())
                );
                Files.copy(
                    outputFile.toPath(),
                    new File(this.basedir, fileName).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                );
                this.index.put(uri, fileName);
            }
        } finally {
            this.index.getLock().unlock();
        }
    }

    /**
     * Retrieves an entry from the cache index based on the provided URI and verifies it
     * using the given checksums.
     * @param uri The URI of the resource to retrieve.
     * @param checksums The checksums used to validate the integrity of the resource.
     * @return An Optional containing the resource path if the resource is valid and found,
     *  otherwise an empty Optional.
     */
    private Optional<String> getEntry(final URI uri, final Checksums checksums) {
        final String resource = this.index.get(uri);
        return Optional.ofNullable(resource)
            .filter(
                res -> {
                    final File resFile = new File(this.basedir, resource);
                    return resFile.isFile() && checksums.isValid(resFile);
                }
            );
    }
}
