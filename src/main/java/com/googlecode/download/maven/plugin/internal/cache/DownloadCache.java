/*
 * Copyright 2012, Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.download.maven.plugin.internal.cache;

import com.googlecode.download.maven.plugin.internal.checksum.Checksums;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * A class representing a download cache
 * @author Mickael Istria (Red Hat Inc)
 *
 */
public final class DownloadCache {

	private final File basedir;
    private final FileIndex index;

	public DownloadCache(File cacheDirectory) {
        DownloadCache.createIfNeeded(cacheDirectory);
        this.index = new FileBackedIndex(cacheDirectory);
        this.basedir = cacheDirectory;
    }

	private String getEntry(URI uri, final Checksums checksums) {
		final String res = this.index.get(uri);
		if (res == null) {
			return null;
		}
		final File resFile = new File(this.basedir, res);
		if (resFile.isFile() && checksums.isValid(resFile)) {
			return res;
		} else {
			return null;
		}
	}

	/**
	 * Get a File in the download cache. If no cache for this URL, or
	 * if expected checksums don't match cached ones, returns null.
	 * available in cache,
	 * @param uri URL of the file
	 * @param checksums Supplied checksums.
	 * @return A File when cache is found, null if no available cache
	 */
    public File getArtifact(URI uri, final Checksums checksums) {
		final String res;
		try {
			this.index.getLock().lock();
			res = this.getEntry(uri, checksums);
		} finally {
			this.index.getLock().unlock();
		}
		if (res != null) {
			return new File(this.basedir, res);
		}
		return null;
	}

    public void install(URI uri, File outputFile, final Checksums checksums) throws Exception {
		try {
			this.index.getLock().lock();
			final String entry = this.getEntry(uri, checksums);
			if (entry != null) {
				return; // entry already here
			}
			final String fileName = String.format(
				"%s_%s", outputFile.getName(), DigestUtils.md5Hex(uri.toString())
			);
			Files.copy(
				outputFile.toPath(),
				new File(this.basedir, fileName).toPath(),
				StandardCopyOption.REPLACE_EXISTING
			);
			// update index
			this.index.put(uri, fileName);
		} finally {
			this.index.getLock().unlock();
		}
	}

    private static void createIfNeeded(final File basedir) {
        if (!basedir.exists()) {
            basedir.mkdirs();
        } else if (!basedir.isDirectory()) {
            throw new IllegalArgumentException(
                String.format(
					"Cannot use %s as cache directory: a file already exist there",
                    basedir
                )
            );
        }
    }
}
