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
import java.net.URL;
import java.security.MessageDigest;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

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
	 * @parameter expression="${download.cache.directory}"
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
    * To look up Archiver/UnArchiver implementation
	*
	* @component
	*/
	private ArchiverManager archiverManager;
	
	/**
	 * For transfers
	 * 
	 * @component
	 */
	private WagonManager wagonManager;
	
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
							doGet(outputFile);
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
				UnArchiver unarchiver = this.archiverManager.getUnArchiver(outputFile);
				unarchiver.setSourceFile(outputFile);
				unarchiver.setDestDirectory(this.outputDirectory);
				unarchiver.extract();
				outputFile.delete();
			}
		} catch (Exception ex) {
			throw new MojoExecutionException("IO Error", ex);
		}
	}

	private void doGet(File outputFile) throws Exception {
		String[] segments = this.url.split("/");
		String file = segments[segments.length - 1];
		String repoUrl = this.url.substring(0, this.url.length() - file.length());
		Repository repository = new Repository(repoUrl, repoUrl);
		
		Wagon wagon = this.wagonManager.getWagon(repository.getProtocol());
		// TODO: this should be retrieved from wagonManager
		com.googlecode.ConsoleDownloadMonitor downloadMonitor = new com.googlecode.ConsoleDownloadMonitor();
		wagon.addTransferListener(downloadMonitor);
		wagon.connect(repository);
		wagon.get(file, outputFile);
		wagon.disconnect();
		wagon.removeTransferListener(downloadMonitor);
	}
}
