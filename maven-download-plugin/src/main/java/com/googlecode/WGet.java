/**
 * Copyright 2009-2012 Marc-Andre Houle and Red Hat Inc
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.MessageDigest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;

/**
  * Will download a file from a web site using the standard HTTP protocol.
  * @goal wget 
  * @phase process-resources
  * @requiresProject false
  * 
  * @author Marc-Andre Houle
  * @author Mickael Istria (Red Hat Inc)
  */

public class WGet extends AbstractMojo{
	
	/**
	  * Represent the URL to fetch information from.
	  * @parameter expression="${download.url}" 
	  * @required
	  */
	private String url;
	
	/**
	  * Represent the file name to use as output value. If not set, will use last segment of "url"
	  * @parameter expression="${download.outputFileName}"
	  */
	private String outputFileName;
	
	/**
	  * Represent the directory where the file should be downloaded.
	  * @parameter expression="${download.outputDirectory}"  default-value="${project.build.directory}"
	  * @required
	  */
	private File outputDirectory;
	
	/**
	 * The md5 of the file. If set, file signature will be compared to this signature
	 * and plugin will fail.
	 * @parameter
	 */
	private String md5;
	
	/**
	 * The sha1 of the file. If set, file signature will be compared to this signature
	 * and plugin will fail.
	 * @parameter
	 */
	private String sha1;
	
	/**
	 * Whether to unpack the file in case it is an archive (.zip)
	 * @parameter default-value="false"
	 */
	private boolean unpack;
	
	/**
	 * How many retries for a download
	 * @parameter default-value="2"
	 */
	private int retries;
	
	/**
	 * Download file without polling cache
	 * @parameter default-value="false"
	 */
	private boolean skipCache;
	
	/**
	 * The directory to use as a cache. Default is ${local-repo}/.cache/maven-download-plugin
	 * @parameter expression=${download.cache.directory}
	 */
	private File cacheDirectory;
	
	/**
	 * Whether to skip execution of Mojo
	 * @parameter expression="${download.plugin.skip}" default-value="false"
	 */
	private boolean skip;
	
	 /**
     * @parameter default-value="${session}"
     */
    private MavenSession session;
    
    /**
    * The Zip unarchiver.
	*
	* @component role="org.codehaus.plexus.archiver.UnArchiver" roleHint="zip"
	*/
	private ZipUnArchiver zipUnArchiver;
	
	/**
	  * Method call whent he mojo is executed for the first time.
	  * @throws MojoExecutionException if an error is occuring in this mojo.
	  * @throws MojoFailureException if an error is occuring in this mojo.
	  */
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (this.skip) {
			getLog().info("maven-download-plugin:wget skipped");
			return;
		}
		
		if (retries < 1) {
			throw new MojoFailureException("retries must be at least 1");
		}
		
		// PREPARE
		if (this.outputFileName == null) {
			try {
				this.outputFileName = new File(new URL(this.url).getFile()).getName();
			} catch (Exception ex) {
				throw new MojoExecutionException("Invalid URL", ex);
			}
		}
		if (this.cacheDirectory == null) {
			this.cacheDirectory = new File(this.session.getLocalRepository().getBasedir(), ".cache/maven-download-plugin");
		}
		getLog().debug("Cache is: " + this.cacheDirectory.getAbsolutePath());
		DownloadCache cache = new DownloadCache(this.cacheDirectory);
		this.outputDirectory.mkdirs();
		File outputFile = new File(this.outputDirectory, this.outputFileName);

		// DO
		try {
			if (outputFile.exists()) {
				// TODO verify last modification date
				getLog().info("File already exist, skipping");
			} else {
				File cached = cache.getArtifact(this.url, this.md5, this.sha1);
				if (!this.skipCache && cached != null && cached.exists()) {
					getLog().info("Got from cache: " + cached.getAbsolutePath());
					FileUtils.copyFile(cached, outputFile);
				} else {
					boolean done = false;
					while (!done && this.retries > 0) {
						try {
							httpGet(this.url, outputFile);
							if (this.md5 != null) {
								SignatureUtils.verifySignature(outputFile, this.md5, MessageDigest.getInstance("MD5"));
							}
							if (this.sha1 != null) {
								SignatureUtils.verifySignature(outputFile, this.sha1, MessageDigest.getInstance("SHA1"));
							}
							done = true;
						} catch (Exception ex) {
							getLog().warn("Could not get content", ex);
							this.retries--;
							if (this.retries > 0) {
								getLog().warn("Retrying (" + this.retries + " more)");
							}
						}
					}
					if (!done) {
						throw new MojoFailureException("Could not get content");
					}
				}
			}
			cache.install(this.url, outputFile, this.md5, this.sha1);
			if (this.unpack) {
				this.zipUnArchiver.setSourceFile(outputFile);
				this.zipUnArchiver.setDestDirectory(this.outputDirectory);
				this.zipUnArchiver.extract();
				outputFile.delete();
			}
		} catch (Exception ex) {
			throw new MojoExecutionException("IO Error", ex);
		}
	}

//	private static void unzip(File outputFile, File targetDirectory)	throws Exception {
//		new ZipUnArchiver().
//		byte[] buf = new byte[1024];
//		ZipInputStream zinstream = new ZipInputStream(new FileInputStream(outputFile));
//		ZipEntry zentry = zinstream.getNextEntry();
//		while (zentry != null) {
//			String entryName = zentry.getName();
//			File targetFile = new File(targetDirectory, entryName);
//			if (!targetFile.getParentFile().isDirectory()) {
//				targetFile.getParentFile().mkdirs();
//			}
//			FileOutputStream outstream = new FileOutputStream(targetFile);
//			int n;
//
//			while ((n = zinstream.read(buf, 0, 1024)) > -1) {
//				outstream.write(buf, 0, n);
//
//			}
//			outstream.close();
//			zinstream.closeEntry();
//			zentry = zinstream.getNextEntry();
//		}
//		zinstream.close();
//	}

	private static void httpGet(String url, File outputFile) throws MojoExecutionException, MojoFailureException {
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet httpget = new HttpGet(url); 
		try {
			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
			// If the response does not enclose an entity, there is no need
			// to worry about connection release
			if (entity != null) {
				InputStream inStream = entity.getContent();
				try{
					OutputStream outStream = FileUtils.openOutputStream(outputFile);
					IOUtils.copy(inStream, outStream);
					outStream.close();
				}finally{
					// Closing the input stream will trigger connection release
					inStream.close();
				}
			}
		} catch (IOException ex) {
			// In case of an IOException the connection will be released
			// back to the connection manager automatically
			throw new MojoExecutionException("Error while copying value.", ex);
		} catch (RuntimeException ex) {
			// In case of an unexpected exception you may want to abort
			// the HTTP request in order to shut down the underlying 
			// connection and release it back to the connection manager.
			httpget.abort();
			throw new MojoFailureException("Interuption caused cancelled download.");
		}
	}
}
