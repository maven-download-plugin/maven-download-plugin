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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.wagon.events.TransferEvent;
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
	 * Codes coming from wikipedia (https://en.wikipedia.org/wiki/HTTP_codes)
	 */
	private static final List<Integer> ACCEPTED_HTTP_CODES = Arrays.asList(
					200,
					201,
					202,
					203,
					204,
					205,
					206,
					207,
					208,
					226,
					230);

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
	 * @parameter expression="${download.md5}"
	 */
	private String md5;
	
	/**
	 * The sha1 of the file. If set, file signature will be compared to this signature
	 * and plugin will fail.
	 * @parameter expression="${download.sha1}"
	 */
	private String sha1;
	
	/**
	 * Whether to unpack the file in case it is an archive (.zip)
	 * @parameter default-value="false" expression="${download.unpack}"
	 */
	private boolean unpack;
	
	/**
	 * How many retries for a download
	 * @parameter default-value="2" expression="${download.retries}"
	 */
	private int retries;
	
	/**
	 * Download file without polling cache
	 * @parameter expression="${download.skipCache}" default-value="false"
	 */
	private boolean skipCache;
	
	/**
	 * Download file even if it already exists at target location
	 * @parameter expression="${download.force}" default-value="false"
	 */
	private boolean forceDownload;

	
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
	 * Map of HTTP parameters to be used to download content. These properties are to be given as
	 * &lt;httpParameters&gt;
	 * 	&lt;aParameterName&gt;aParameterValue&lt;/aParameterName&gt;
	 * &lt;httpParameters&gt;
	 * @parameter
	 */
	private Map<String, String> parameters = new HashMap<String, String>();
	
	/**
	 * Map of HTTP headers to be used to download content. These properties are to be given as
	 * &lt;httpHeaders&gt;
	 * 	&lt;aHeaderName&gt;aParameterValue&lt;/aHeaderName&gt;
	 * &lt;/httpHeaders&gt;
	 * @parameter
	 */
	private Map<String, String> headers = new HashMap<String, String>();
	
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
		// added because elsewhere the output directory creation didn't happened when outputDirectory wasn't specified
		if(outputDirectory==null)
			outputDirectory=new File(outputFileName).getParentFile();
		this.outputDirectory.mkdirs();
		File outputFile = new File(this.outputDirectory, this.outputFileName);

		// DO
		try {
			if (outputFile.exists() && !forceDownload) {
				// TODO verify last modification date
				getLog().info("File already exist, skipping");
			} else {
				File cached = cache.getArtifact(this.url, this.md5, this.sha1);
				if (!this.skipCache && cached != null && cached.exists()) {
					getLog().info("Got from cache: " + cached.getAbsolutePath());
					FileUtils.copyFile(cached, outputFile);
				} else {
					boolean done = false;
					URL downloadURL = new URL(url);
					// No more need to handcode retry, as HTTPClient natively supports that feature (cool no ?)
					done = downloadAndValidate(downloadURL, outputFile, retries, parameters, headers, md5, sha1);
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

	/**
	 * Download the content of source to output file and validate it (if either md5 or sha1 is non null
	 * @param source source url to dowload content from
	 * @param outputFile output file to download content to
	 * @param retries 
	 * @param parameters map of http parameters to send
	 * @param headers http headers used for that request
	 * @param md5 an md5 to validate the downloaded data. Can be null.
	 * @param sha1 a sh1 to validate the downloaded data. Can be null.
	 * @return true if something was downloaded without any exception, false elsewhen.
	 */
	private boolean downloadAndValidate(URL source, File outputFile, int retries, Map<String, String> parameters, Map<String, String> headers, String md5, String sha1) {
		try {
			doGet(source, outputFile, parameters, headers, retries);
			if (this.md5 != null) {
				SignatureUtils.verifySignature(outputFile, this.md5, MessageDigest.getInstance("MD5"));
			}
			if (this.sha1 != null) {
				SignatureUtils.verifySignature(outputFile, this.sha1, MessageDigest.getInstance("SHA1"));
			}
			return true;
		} catch (Exception ex) {
			getLog().warn("Could not get content", ex);
			return false;
		}
	}

	/**
	 * Perform the get operation and outputs a bunch of debug messages
	 * @param source the source to download data from
	 * @param outputFile the output destination
	 * @param parameters http parameters used for that request
	 * @param headers http headers used for that request
	 * @param retries number of times that query will be retried
	 * @throws Exception
	 */
	private void doGet(URL source, File outputFile, Map<String, String> parameters, Map<String, String> headers, int retries) throws Exception {
		ConsoleDownloadMonitor monitor = new ConsoleDownloadMonitor();
		HttpGet getRequest = new HttpGet(source.toURI());
		// putting parameters in game
		for(Map.Entry<String, String> p : parameters.entrySet()) {
			getRequest.getParams().setParameter(p.getKey(), p.getValue());
		}
		// and headers next to them
		for(Map.Entry<String, String> h : headers.entrySet()) {
			getRequest.addHeader(h.getKey(), h.getValue());
		}
		if(getLog().isDebugEnabled()) {
			StringBuilder sOut = new StringBuilder();
			sOut.append("running ").append(getRequest).append("\n");
			sOut.append("query headers :\n").append(appendHeaders(getRequest.getAllHeaders()));
			sOut.append("query parameters can't be obtained, sorry\n");
			getLog().debug(sOut);
		}
		// Creating client on-demand to allow easy retry
		HttpClient client = getHttpClient(retries);
		monitor.outputTransferStarted(source.toString(), TransferEvent.REQUEST_GET);
		HttpResponse response = client.execute(getRequest);
		// Put some more log info about response before to consume content
		if(getLog().isDebugEnabled()) {
			StringBuilder sOut = new StringBuilder("query executed with result\n");
			StatusLine status = response.getStatusLine();
			sOut.append(status.getProtocolVersion().toString()).append("\t").append(status.getStatusCode()).append(" ").append(status.getReasonPhrase()).append("\n");
			sOut.append("response headers :\n");
			sOut.append(appendHeaders(response.getAllHeaders()));
			getLog().debug(sOut);
		}
		// now make sure result was OK (if not an exception will be thrown
		if(ACCEPTED_HTTP_CODES.contains(response.getStatusLine().getStatusCode())) {
			HttpEntity entity = response.getEntity();
			if(getLog().isDebugEnabled()) {
				StringBuilder sOut = new StringBuilder("query entity content is\n");
				sOut.append(appendHeaders(new Header[] {entity.getContentType(), entity.getContentEncoding()}));
			}
			// Now read entity content
			InputStream stream = entity.getContent();
			BufferedInputStream bufferedInput = new BufferedInputStream(stream);
			outputFile.createNewFile();
			OutputStream openOutputStream = monitor.decorate(FileUtils.openOutputStream(outputFile), entity.getContentLength());
			try {
				IOUtils.copy(bufferedInput,openOutputStream);
			} finally {
				monitor.outputTransferTerminated(entity.getContentLength(), TransferEvent.REQUEST_GET);
				openOutputStream.close();
				bufferedInput.close();
			}
		} else {
			throw new UnsupportedOperationException("server sent status code "+response.getStatusLine().getStatusCode()+" which we do not support");
		}
	}

	/**
	 * Construct the http client with the specified number of retries
	 * @param retries number of times the query will be re-send to server
	 * @return a working HTTP client
	 */
	private HttpClient getHttpClient(final int retries) {
		DefaultHttpClient returned = new DefaultHttpClient();
		returned.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(retries, false));
		return returned;
	}

	/**
	 * Append headers to a string, for easier reading
	 * @param headers
	 * @return
	 */
	private StringBuilder appendHeaders(Header[] headers) {
		StringBuilder sOut = new StringBuilder();
		for(Header h : headers) {
			sOut.append(h).append("\n");
		}
		return sOut;
	}
}
