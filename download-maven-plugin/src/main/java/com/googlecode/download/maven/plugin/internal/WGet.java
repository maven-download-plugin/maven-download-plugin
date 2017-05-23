/**
 * Copyright 2009-2016 Marc-Andre Houle and Red Hat Inc
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
package com.googlecode.download.maven.plugin.internal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
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
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.events.TransferEvent;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.StringUtils;

import com.googlecode.download.maven.plugin.internal.cache.DownloadCache;

/**
 * Will download a file from a web site using the standard HTTP protocol.
 *
 * @author Marc-Andre Houle
 * @author Mickael Istria (Red Hat Inc)
 */
@Mojo(name = "wget", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = false)
public class WGet extends AbstractMojo {

    /**
     * Represent the URL to fetch information from.
     */
    @Parameter(alias = "url", property = "download.url", required = true)
    private URI uri;

    /**
     * Flag to overwrite the file by redownloading it
     */
    @Parameter(property = "download.overwrite")
    private boolean overwrite;

    /**
     * Represent the file name to use as output value. If not set, will use last
     * segment of "url"
     */
    @Parameter(property = "download.outputFileName")
    private String outputFileName;

    /**
     * Represent the directory where the file should be downloaded.
     */
    @Parameter(property = "download.outputDirectory", defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    /**
     * The md5 of the file. If set, file signature will be compared to this
     * signature and plugin will fail.
     */
    @Parameter
    private String md5;

    /**
     * The sha1 of the file. If set, file signature will be compared to this
     * signature and plugin will fail.
     */
    @Parameter
    private String sha1;

    /**
     * The sha512 of the file. If set, file signature will be compared to this
     * signature and plugin will fail.
     */
    @Parameter
    private String sha512;

    /**
     * Whether to unpack the file in case it is an archive (.zip)
     */
    @Parameter(defaultValue = "false")
    private boolean unpack;

	/**
     * Server Id from settings file to use for authentication
     * Only one of serverId or (username/password) may be supplied
     */
    @Parameter
    private String serverId;

    /**
     * Custom username for the download
     */
    @Parameter
    private String username;

    /**
     * Custom password for the download
     */
    @Parameter
    private String password;

    /**
     * How many retries for a download
     */
    @Parameter(defaultValue = "2")
    private int retries;

    /**
     * Read timeout for a download in milliseconds
     */
    @Parameter(defaultValue = "0")
    private int readTimeOut;

    /**
     * Download file without polling cache
     */
    @Parameter(defaultValue = "false")
    private boolean skipCache;

    /**
     * The directory to use as a cache. Default is
     * ${local-repo}/.cache/maven-download-plugin
     */
    @Parameter(property = "download.cache.directory")
    private File cacheDirectory;

    /**
     * Flag to determine whether to fail on an unsuccessful download.
     */
    @Parameter(defaultValue = "true")
    private boolean failOnError;

    /**
     * Whether to skip execution of Mojo
     */
    @Parameter(property = "download.plugin.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Whether to check the signature of existing files
     */
    @Parameter(property = "checkSignature", defaultValue = "false")
    private boolean checkSignature;

    @Parameter(property = "session")
    private MavenSession session;

    @Component
    private ArchiverManager archiverManager;

    /**
     * For transfers
     */
    @Component
    private WagonManager wagonManager;

    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

	/**
	 * Map of HTTP parameters to be used to download content. These properties are to be given as
	 * &lt;httpParameters&gt;
	 * 	&lt;aParameterName&gt;aParameterValue&lt;/aParameterName&gt;
	 * &lt;httpParameters&gt;
	 */
    @Parameter(property = "parameters")
	private final Map<String, String> parameters = new HashMap<>();

	/**
	 * Map of HTTP headers to be used to download content. These properties are to be given as
	 * &lt;httpHeaders&gt;
	 * 	&lt;aHeaderName&gt;aParameterValue&lt;/aHeaderName&gt;
	 * &lt;/httpHeaders&gt;
	 */
    @Parameter(property = "headers")
	private final Map<String, String> headers = new HashMap<>();

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

        if (StringUtils.isNotBlank(serverId) && (StringUtils.isNotBlank(username) || StringUtils.isNotBlank(password))) {
            throw new MojoExecutionException("Specify either serverId or username/password, not both");
        }

        if (settings == null) {
            getLog().warn("settings is null");
        }
        getLog().debug("Got settings");
		if (retries < 1) {
			throw new MojoFailureException("retries must be at least 1");
		}

		// PREPARE
        if (this.outputFileName == null) {
			try {
                this.outputFileName = new File(this.uri.toURL().getFile()).getName();
            } catch (Exception ex) {
				throw new MojoExecutionException("Invalid URL", ex);
			}
		}
        if (this.cacheDirectory == null) {
            this.cacheDirectory = new File(this.session.getLocalRepository()
                .getBasedir(), ".cache/download-maven-plugin");
        }
        getLog().debug("Cache is: " + this.cacheDirectory.getAbsolutePath());
        DownloadCache cache = new DownloadCache(this.cacheDirectory);
        this.outputDirectory.mkdirs();
        File outputFile = new File(this.outputDirectory, this.outputFileName);

		// DO
		try {
            boolean haveFile = outputFile.exists();
            if (haveFile) {
                boolean signatureMatch = true;
                if (this.checkSignature) {
                    String expectedDigest = null, algorithm = null;
                    if (this.md5 != null) {
                        expectedDigest = this.md5;
                        algorithm = "MD5";
                    }

                    if (this.sha1 != null) {
                        expectedDigest = this.sha1;
                        algorithm = "SHA1";
                    }

                    if (this.sha512 != null) {
                        expectedDigest = this.sha512;
                        algorithm = "SHA-512";
                    }

                    if (expectedDigest != null) {
                        try {
                            SignatureUtils.verifySignature(outputFile, expectedDigest,
                                MessageDigest.getInstance(algorithm));
                        } catch (MojoFailureException e) {
                            getLog().warn("The local version of file " + outputFile.getName() + " doesn't match the expected signature. " +
                                "You should consider checking the specified signature is correctly set.");
                            signatureMatch = false;
                        }
                    }
                }

                // TODO verify last modification date
                if (!signatureMatch) {
                    outputFile.delete();
                    haveFile = false;
                } else if (!overwrite) {
                    getLog().info("File already exist, skipping");
                } else {
                    // If no signature provided and owerwriting requested we
                    // will treat the fact as if there is no file in the cache.
                    haveFile = false;
                }
            }

            if (!haveFile) {
                File cached = cache.getArtifact(this.uri, this.md5, this.sha1, this.sha512);
                if (!this.skipCache && cached != null && cached.exists()) {
                    getLog().info("Got from cache: " + cached.getAbsolutePath());
                    Files.copy(cached.toPath(), outputFile.toPath());
                } else {
                    boolean done = false;
                    while (!done && this.retries > 0) {
                        try {
                            doGet(uri.toURL(), outputFile,
                            		parameters, headers, retries);
                            if (this.md5 != null) {
                                SignatureUtils.verifySignature(outputFile, this.md5,
                                    MessageDigest.getInstance("MD5"));
                            }
                            if (this.sha1 != null) {
                                SignatureUtils.verifySignature(outputFile, this.sha1,
                                    MessageDigest.getInstance("SHA1"));
                            }
                            if (this.sha512 != null) {
                                SignatureUtils.verifySignature(outputFile, this.sha512,
                                    MessageDigest.getInstance("SHA-512"));
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
                        if (failOnError) {
                            throw new MojoFailureException("Could not get content");
                        } else {
                            getLog().warn("Not failing download despite download failure.");
                            return;
                        }
                    }
                }
            }
            cache.install(this.uri, outputFile, this.md5, this.sha1, this.sha512);
            if (this.unpack) {
                unpack(outputFile);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("IO Error", ex);
        }
    }


    private void unpack(File outputFile) throws NoSuchArchiverException {
        UnArchiver unarchiver = this.archiverManager.getUnArchiver(outputFile);
        unarchiver.setSourceFile(outputFile);
        unarchiver.setDestDirectory(this.outputDirectory);
        unarchiver.extract();
        outputFile.delete();
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
	private void doGet(final URL source, final File outputFile, final Map<String, String> parameters, final Map<String, String> headers, final int retries) throws Exception {
        ConsoleDownloadMonitor downloadMonitor = null;
        if (session.getSettings().isInteractiveMode()) {
            downloadMonitor = new ConsoleDownloadMonitor(getLog());
        }
		final HttpGet getRequest = new HttpGet(source.toURI());
		// putting parameters in game
		for(final Map.Entry<String, String> p : parameters.entrySet()) {
			getRequest.getParams().setParameter(p.getKey(), p.getValue());
		}
		// and headers next to them
		for(final Map.Entry<String, String> h : headers.entrySet()) {
			getRequest.addHeader(h.getKey(), h.getValue());
		}
		if(getLog().isDebugEnabled()) {
			final StringBuilder sOut = new StringBuilder();
			sOut.append("running ").append(getRequest).append("\n");
			sOut.append("query headers :\n").append(appendHeaders(getRequest.getAllHeaders()));
			sOut.append("query parameters can't be obtained, sorry\n");
			getLog().debug(sOut);
		}
		// Creating client on-demand to allow easy retry
		final HttpClient client = getHttpClient(retries);
		downloadMonitor.transferInitiated(outputFile.getName(), source.toString(), TransferEvent.REQUEST_GET);
		final HttpResponse response = client.execute(getRequest);
		// Put some more log info about response before to consume content
		if(getLog().isDebugEnabled()) {
			final StringBuilder sOut = new StringBuilder("query executed with result\n");
			final StatusLine status = response.getStatusLine();
			sOut.append(status.getProtocolVersion().toString()).append("\t").append(status.getStatusCode()).append(" ").append(status.getReasonPhrase()).append("\n");
			sOut.append("response headers :\n");
			sOut.append(appendHeaders(response.getAllHeaders()));
			getLog().debug(sOut);
		}
		// now make sure result was OK (if not an exception will be thrown)
		final int statusCode = response.getStatusLine().getStatusCode();
		if(statusCode>=200 && statusCode<300) {
			final HttpEntity entity = response.getEntity();
			if(getLog().isDebugEnabled()) {
				final StringBuilder sOut = new StringBuilder("query entity content is\n");
				sOut.append(appendHeaders(new Header[] {entity.getContentType(), entity.getContentEncoding()}));
			}
			// Now read entity content
			final InputStream stream = entity.getContent();
			final BufferedInputStream bufferedInput = new BufferedInputStream(stream);
			outputFile.createNewFile();
			final OutputStream openOutputStream = downloadMonitor.decorate(FileUtils.openOutputStream(outputFile), entity.getContentLength());
			try {
				IOUtils.copy(bufferedInput,openOutputStream);
			} finally {
				downloadMonitor.transferCompleted(entity.getContentLength(), TransferEvent.REQUEST_GET);
				openOutputStream.close();
				bufferedInput.close();
			}
		} else {
			throw new UnsupportedOperationException("server sent status code "+statusCode+" which we do not support");
		}
	}

	/**
	 * Construct the http client with the specified number of retries
	 * @param retries number of times the query will be re-send to server
	 * @return a working HTTP client
	 */
	private HttpClient getHttpClient(final int retries) {
		final DefaultHttpClient returned = new DefaultHttpClient();
		returned.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(retries, false));
		return returned;
	}

	/**
	 * Append headers to a string, for easier reading
	 * @param headers
	 * @return
	 */
	private StringBuilder appendHeaders(final Header[] headers) {
		final StringBuilder sOut = new StringBuilder();
		for(final Header h : headers) {
			sOut.append(h).append("\n");
		}
		return sOut;
	}
}
