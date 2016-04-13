/**
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

import com.googlecode.download.maven.plugin.internal.SignatureUtils;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * A class representing a download cache
 * @author Mickael Istria (Red Hat Inc)
 *
 */
public class DownloadCache {

	private final File basedir;
    private final FileIndex index;

	public DownloadCache(File cacheDirectory) {
        DownloadCache.createIfNeeded(cacheDirectory);
        this.index = new FileBackedIndex(cacheDirectory);
        this.basedir = cacheDirectory;
    }

	private String getEntry(URL url, String md5, String sha1, String sha512) throws Exception {
		if (!this.index.contains(url)) {
			return null;
		}
		final String res = this.index.get(url);
		File resFile = new File(this.basedir, res);
		if (!resFile.isFile()) {
			return null;
		}
		if (md5 != null && !md5.equals(SignatureUtils.getMD5(resFile))) {
			return null;
		}
		if (sha1 != null && !sha1.equals(SignatureUtils.getSHA1(resFile))) {
			return null;
		}
		if (sha512 != null && !sha512.equals(SignatureUtils.getSHA512(resFile))) {
			return null;
		}
		return res;
	}

	/**
	 * Get a File in the download cache. If no cache for this URL, or
	 * if expected signatures don't match cached ones, returns null.
	 * available in cache,
	 * @param url URL of the file
	 * @param md5 MD5 signature to verify file. Can be null =&gt; No check
	 * @param sha1 Sha1 signature to verify file. Can be null =&gt; No check
	 * @return A File when cache is found, null if no available cache
	 */
    public File getArtifact(URL url, String md5, String sha1, String sha512) throws Exception {
		String res = getEntry(url, md5, sha1, sha512);
		if (res != null) {
			return new File(this.basedir, res);
		}
		return null;
	}

    public void install(URL url, File outputFile, String md5, String sha1, String sha512) throws Exception {
		if (md5 == null) {
			md5 = SignatureUtils.computeSignatureAsString(outputFile, MessageDigest.getInstance("MD5"));
		}
		if (sha1 == null) {
			sha1 = SignatureUtils.computeSignatureAsString(outputFile, MessageDigest.getInstance("SHA1"));
		}
		if (sha512 == null) {
			sha512 = SignatureUtils.computeSignatureAsString(outputFile, MessageDigest.getInstance("SHA-512"));
		}
		String entry = getEntry(url, md5, sha1, sha512);
		if (entry != null) {
			return; // entry already here
		}
		String fileName = outputFile.getName() + '_' + DigestUtils.md5Hex(url.toString());
		Files.copy(outputFile.toPath(), new File(this.basedir, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
		// update index
		this.index.put(url, fileName);
	}

    private static void createIfNeeded(final File basedir) {
        if (!basedir.exists()) {
            basedir.mkdirs();
        } else if (!basedir.isDirectory()) {
            throw new IllegalArgumentException(
                String.format(
                    "Cannot use %s as cache directory: file is already exist",
                    basedir
                )
            );
        }
    }
}
