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
package com.googlecode;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * A class representing a download cache
 * @author Mickael Istria (Red Hat Inc)
 *
 */
public class DownloadCache {

	private static final String INDEX_FILENAME = "index.ser";

	private File basedir;
	private File indexFile;
	private Map<String, CachedFileEntry> index;

	public DownloadCache(File cacheDirectory) throws MojoExecutionException {
		this.basedir = cacheDirectory;
		this.indexFile = new File(this.basedir, INDEX_FILENAME);

		if (this.basedir.exists() && !this.basedir.isDirectory()) {
			throw new MojoExecutionException("Cannot use " + this.basedir + " as cache directory: file exists");
		}
		if (!this.basedir.exists()) {
			this.basedir.mkdirs();
		}
	}

	private CachedFileEntry getEntry(String url, String md5, String sha1) throws Exception {
		loadIndex();
		CachedFileEntry res = this.index.get(url);
		if (res == null) {
			return null;
		}
		File resFile = new File(this.basedir, res.fileName);
		if (!resFile.isFile()) {
			return null;
		}
		if (md5 != null && !md5.equals(SignatureUtils.getMD5(resFile))) {
			return null;
		}
		if (sha1 != null && !sha1.equals(SignatureUtils.getSHA1(resFile))) {
			return null;
		}
		return res;
	}

	/**
	 * Get a File in the download cache. If no cache for this URL, or
	 * if expected signatures don't match cached ones, returns null.
	 * available in cache,
	 * @param url URL of the file
	 * @param md5 MD5 signature to verify file. Can be null => No check
	 * @param sha1 Sha1 signature to verify file. Can be null => No check
	 * @return A File when cache is found, null if no available cache
	 */
	public File getArtifact(String url, String md5, String sha1) throws Exception {
		CachedFileEntry res = getEntry(url, md5, sha1);
		if (res != null) {
			return new File(this.basedir, res.fileName);
		}
		return null;
	}

	public void install(String url, File outputFile, String md5, String sha1) throws Exception {
		if (md5 == null) {
			md5 = SignatureUtils.computeSignatureAsString(outputFile, MessageDigest.getInstance("MD5"));
		}
		if (sha1 == null) {
			sha1 = SignatureUtils.computeSignatureAsString(outputFile, MessageDigest.getInstance("SHA1"));
		}
		CachedFileEntry entry = getEntry(url, md5, sha1);
		if (entry != null) {
			return; // entry already here
		}
		entry = new CachedFileEntry();
		entry.fileName = outputFile.getName() + '_' + DigestUtils.md5Hex(url);
		Files.copy(outputFile.toPath(), new File(this.basedir, entry.fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
		// update index
		loadIndex();
		this.index.put(url, entry);
		saveIndex();
	}

	private void loadIndex() throws Exception {
        if (this.indexFile.isFile()) {
            try (FileLock lock = new RandomAccessFile(this.indexFile, "r").getChannel().lock(0, Long.MAX_VALUE, true)) {
                try (ObjectInputStream deserialize = new ObjectInputStream(new FileInputStream(this.indexFile))) {
                    this.index = (Map<String, CachedFileEntry>) deserialize.readObject();
                }
            }
		} else {
			this.index = new HashMap<>();
		}

	}

	private void saveIndex() throws Exception {
		if (!this.indexFile.exists()) {
			this.indexFile.createNewFile();
		}
		FileOutputStream out = new FileOutputStream(this.indexFile);
		try (ObjectOutputStream res = new ObjectOutputStream(out)) {
            try (FileLock lock = out.getChannel().lock()) {
                res.writeObject(index);
            }
        }
	}

}
