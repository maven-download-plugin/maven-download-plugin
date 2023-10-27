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

import com.googlecode.download.maven.plugin.internal.checksum.Checksums;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.bzip2.BZip2UnArchiver;
import org.codehaus.plexus.archiver.gzip.GZipUnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.snappy.SnappyUnArchiver;
import org.codehaus.plexus.archiver.xz.XZUnArchiver;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.sonatype.plexus.build.incremental.BuildContext;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.maven.shared.utils.StringUtils.isBlank;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;

/**
 * Will download a file from a web site using the standard HTTP protocol.
 *
 * @author Marc-Andre Houle
 * @author Mickael Istria (Red Hat Inc)
 */
@Mojo(name = "wget", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = false, threadSafe = true)
public class WGetMojo extends AbstractMojo {
    /**
     * A map of file locks by files to be downloaded.
     * Ensures exclusive access to a target file.
     */
    private static final Map<String, Lock> FILE_LOCKS = new ConcurrentHashMap<>();

    /**
     * Represent the URL to fetch information from.
     */
    @Parameter(alias = "url", property = "download.url", required = true)
    private URI uri;

    /**
     * Flag to overwrite the file by redownloading it.
     * {@code overwrite=true} means that if the target file pre-exists
     * at the expected target-location for the current plugin execution,
     * then the pre-existing file will be overwritten and replaced anyway;
     * whereas default {@code overwrite=false} will entirely skip all the
     * execution if the target file pre-exists and matches specification
     * (name, signatures...).
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
     * The md5 of the file. If set, file checksum will be compared to this
     * checksum and plugin will fail.
     */
    @Parameter(property = "download.verify.md5")
    private String md5;

    /**
     * The sha1 of the file. If set, file checksum will be compared to this
     * checksum and plugin will fail.
     */
    @Parameter(property = "download.verify.sha1")
    private String sha1;

    /**
     * The sha256 of the file. If set, file checksum will be compared to this
     * checksum and plugin will fail.
     */
    @Parameter(property = "download.verify.sha256")
    private String sha256;

    /**
     * The sha512 of the file. If set, file checksum will be compared to this
     * checksum and plugin will fail.
     */
    @Parameter(property = "download.verify.sha512")
    private String sha512;

    /**
     * Whether to unpack the file in case it is an archive (.zip)
     */
    @Parameter(property = "download.unpack", defaultValue = "false")
    private boolean unpack;

    /**
     * Server Id from settings file to use for authentication
     * Only one of serverId or (username/password) may be supplied
     */
    @Parameter(property = "download.auth.serverId")
    private String serverId;

    /**
     * Custom username for the download
     */
    @Parameter(property = "download.auth.username")
    private String username;

    /**
     * Custom password for the download
     */
    @Parameter(property = "download.auth.password")
    private String password;

    /**
     * How many retries for a download
     */
    @Parameter(property = "download.retries", defaultValue = "2")
    private int retries;

    /**
     * Read timeout for a download in milliseconds
     */
    @Parameter(defaultValue = "3000")
    private int readTimeOut;

    /**
     * Download file without polling cache.
     * Means that the download operation will not look in the global cache
     * to resolve the file to download, and will directly proceed with
     * the download and won't store this download in the cache.
     * It's recommended for urls that have "volatile" content.
     */
    @Parameter(property = "download.cache.skip", defaultValue = "false")
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
     * Whether to verify the checksum of an existing file
     * <p>
     * By default, checksum verification only occurs after downloading a file. This option additionally enforces
     * checksum verification for already existing, previously downloaded (or manually copied) files. If the checksum
     * does not match, re-download the file.
     * <p>
     * Use this option in order to ensure that a new download attempt is made after a previously interrupted build or
     * network connection or some other event corrupted a file.
     */
    @Parameter(property = "alwaysVerifyChecksum", defaultValue = "false")
    private boolean alwaysVerifyChecksum;

    /**
     * @deprecated The option name is counter-intuitive and not related to signatures but to checksums, in fact.
     * Please use {@link #alwaysVerifyChecksum} instead. This option might be removed in a future release.
     */
    @Parameter(property = "checkSignature", defaultValue = "false")
    @Deprecated
    private boolean checkSignature;

    /**
     * <p>Whether to follow redirects (301 Moved Permanently, 302 Found, 303 See Other).</p>
     * <p>If this option is disabled and the returned resource returns a redirect, the plugin will report an error
     * and exit unless {@link #failOnError} is {@code false}.</p>
     */
    @Parameter(property = "download.plugin.followRedirects", defaultValue = "true")
    private boolean followRedirects = true;

    /**
     * A list of additional HTTP headers to send with the request
     */
    @Parameter(property = "download.plugin.headers")
    private Map<String, String> headers = new HashMap<>();

    @Parameter(property = "session", readonly = true)
    private MavenSession session;

    @Inject
    private ArchiverManager archiverManager;

    /**
     * For transfers
     */

    @Inject
    private BuildContext buildContext;

    /**
     * Runs the plugin only if the current project is the execution root.
     *
     * This is helpful, if the plugin is defined in a profile and should only run once
     * to download a shared file.
     */
    @Parameter(property = "runOnlyAtRoot", defaultValue = "false")
    private boolean runOnlyAtRoot;

    /**
     * Maximum time (ms) to wait to acquire a file lock.
     *
     * Customize the time when using the plugin to download the same file
     * from several submodules in parallel build.
     */
    @Parameter(property = "maxLockWaitTime", defaultValue = "30000")
    private long maxLockWaitTime;

    /**
     * {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting shall happen.
     *
     * @since 1.6.8
     */
    @Parameter(property = "download.fileMappers")
    private FileMapper[] fileMappers;

    /**
     * If {@code true}, preemptive authentication will be used
     *
     * @since 1.6.9
     */
    @Parameter(property = "preemptiveAuth", defaultValue = "false")
    private boolean preemptiveAuth;

    
    /**
     * Ensures that the output directory does not contain unresolved path variables, i.e. when running without a pom.xml.
     * If unresolved path variables are detected, set the output directory to the current working directory.
     * 
     * @since 1.7.2
     * @throws MojoExecutionException If the current working directory could not be resolved. This should never happen.
     */
    private void adjustOutputDirectory() throws MojoExecutionException {
      if (this.outputDirectory.getPath().contains("${")) {
        getLog().info(format("Could not resolve outputDirectory '%s'. Consider using -Ddownload.outputDirectory=.", this.outputDirectory.getPath()));
        this.outputDirectory = new File(".");
        try {
          getLog().info("Adjusting outputDirectory to " + this.outputDirectory.getCanonicalPath());
        } catch (IOException e) {
          throw new MojoExecutionException("Current working directory could not be resolved. This should never happen.");
        }
      }
    }
    
    /**
     * Method call when the mojo is executed for the first time.
     *
     * @throws MojoExecutionException if an error is occuring in this mojo.
     * @throws MojoFailureException   if an error is occuring in this mojo.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            getLog().info("maven-download-plugin:wget skipped");
            return;
        }

        if (this.runOnlyAtRoot && !this.session.getCurrentProject().isExecutionRoot()) {
            getLog().info("maven-download-plugin:wget skipped (not project root)");
            return;
        }

        if (isNotBlank(this.serverId) && (isNotBlank(this.username) || isNotBlank(this.password))) {
            throw new MojoExecutionException("Specify either serverId or username/password, not both");
        }

        if (this.session.getSettings() == null) {
            getLog().warn("settings is null");
        }
        if (this.session.getSettings().isOffline()) {
            getLog().debug("maven-download-plugin:wget offline mode");
        }
        getLog().debug("Got settings");
        if (this.retries < 1) {
            throw new MojoFailureException("retries must be at least 1");
        }

        // PREPARE
        adjustOutputDirectory();
        if (this.outputFileName == null) {
            this.outputFileName = FileNameUtils.getOutputFileName(this.uri);
        }
        if (!this.skipCache) {
            if (this.cacheDirectory == null) {
                this.cacheDirectory = new File(this.session.getLocalRepository()
                        .getBasedir(), ".cache/download-maven-plugin");
            } else if (this.cacheDirectory.exists() && !this.cacheDirectory.isDirectory()) {
                throw new MojoFailureException(String.format("cacheDirectory is not a directory: "
                        + this.cacheDirectory.getAbsolutePath()));
            }
            getLog().debug("Cache is: " + this.cacheDirectory.getAbsolutePath());

        } else {
            getLog().debug("Cache is skipped");
        }
        this.outputDirectory.mkdirs();
        final File outputFile = new File(this.outputDirectory, this.outputFileName);
        final Lock fileLock = FILE_LOCKS.computeIfAbsent(
            outputFile.getAbsolutePath(), ignored -> new ReentrantLock()
        );

        final Checksums checksums = new Checksums(
            this.md5, this.sha1, this.sha256, this.sha512, this.getLog()
        );
        // DO
        boolean lockAcquired = false;
        try {
            lockAcquired = fileLock.tryLock(
                this.maxLockWaitTime, TimeUnit.MILLISECONDS
            );
            if (!lockAcquired) {
                final String message = String.format(
                    "Could not acquire lock for File: %s in %dms",
                    outputFile, this.maxLockWaitTime
                );
                if (this.failOnError) {
                    throw new MojoExecutionException(message);
                } else {
                    getLog().warn(message);
                    return;
                }
            }
            boolean haveFile = outputFile.exists();
            if (haveFile) {
                boolean checksumMatch = true;
                if (this.alwaysVerifyChecksum || this.checkSignature) {
                    try {
                        checksums.validate(outputFile);
                    } catch (final MojoFailureException e) {
                        getLog().warn("The local version of file " + outputFile.getName()
                                + " doesn't match the expected checksum. "
                                + "You should consider checking the specified checksum is correctly set.");
                        checksumMatch = false;
                    }
                }
                if (!checksumMatch || this.overwrite) {
                    outputFile.delete();
                    haveFile = false;
                } else {
                    getLog().info("File already exist, skipping");
                }
            }

            if (!haveFile) {
                if (this.session.getRepositorySession().isOffline()) {
                    if (this.failOnError) {
                        throw new MojoExecutionException("No file in cache and maven is in offline mode");
                    } else {
                        getLog().warn("Ignoring download failure.");
                    }
                }
                boolean done = false;
                for (int retriesLeft = this.retries; !done && retriesLeft > 0; --retriesLeft) {
                    try {
                        this.doGet(outputFile);
                        checksums.validate(outputFile);
                        done = true;
                    } catch (DownloadFailureException ex) {
                        // treating HTTP codes >= 500 as transient and thus always retriable
                        if (this.failOnError && ex.getHttpCode() < 500) {
                            throw new MojoExecutionException(ex.getMessage(), ex);
                        } else {
                            getLog().warn(ex.getMessage());
                        }
                    } catch (IOException ex) {
                        if (this.failOnError) {
                            throw new MojoExecutionException(ex.getMessage(), ex);
                        } else {
                            getLog().warn(ex.getMessage());
                        }
                    }
                    if (!done) {
                        getLog().warn("Retrying (" + (retriesLeft - 1) + " more)");
                    }
                }
                if (!done) {
                    if (this.failOnError) {
                        throw new MojoFailureException("Could not get content after " + this.retries + " failed attempts.");
                    } else {
                        getLog().warn("Ignoring download failure(s).");
                        return;
                    }
                }
            }
            if (this.unpack) {
                unpack(outputFile);
                this.buildContext.refresh(this.outputDirectory);
            } else {
            	this.buildContext.refresh(outputFile);
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (IOException ex) {
            throw new MojoExecutionException("IO Error: ", ex);
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("No such archiver: " + e.getMessage());
        } catch (Exception e) {
            throw new MojoExecutionException("General error: ", e);
        } finally {
            if (lockAcquired) {
                fileLock.unlock();
            }
        }
    }

    private void unpack(File outputFile) throws NoSuchArchiverException {
        UnArchiver unarchiver = this.archiverManager.getUnArchiver(outputFile);
        unarchiver.setSourceFile(outputFile);
        if (isFileUnArchiver(unarchiver)) {
            unarchiver.setDestFile(new File(this.outputDirectory, this.outputFileName.substring(0,
                    this.outputFileName.lastIndexOf('.'))));
        } else {
            unarchiver.setDestDirectory(this.outputDirectory);
        }
        unarchiver.setFileMappers(this.fileMappers);
        unarchiver.extract();
        outputFile.delete();
    }

    private boolean isFileUnArchiver(final UnArchiver unarchiver) {
        return unarchiver instanceof  BZip2UnArchiver ||
                unarchiver instanceof GZipUnArchiver ||
                unarchiver instanceof SnappyUnArchiver ||
                unarchiver instanceof XZUnArchiver;
    }

    private static RemoteRepository createRemoteRepository(String serverId, URI uri)
    {
        return new RemoteRepository.Builder(isBlank(serverId)
                    ? null
                    : serverId,
                isBlank(serverId)
                    ? uri.getScheme()
                    : null,
                isBlank(serverId)
                    ? uri.getHost()
                    : null)
                .build();
    }

    private void doGet(final File outputFile) throws IOException, MojoExecutionException {
        final HttpFileRequester.Builder fileRequesterBuilder = new HttpFileRequester.Builder();

        final RemoteRepository repository = createRemoteRepository(this.serverId, this.uri);

        // set proxy if present
        Optional.ofNullable(this.session.getRepositorySession().getProxySelector())
                .map(selector -> selector.getProxy(repository))
                .ifPresent(proxy -> addProxy(fileRequesterBuilder, repository, proxy));

        Optional.ofNullable(this.session.getRepositorySession().getAuthenticationSelector())
                .map(selector -> selector.getAuthentication(repository))
                .ifPresent(auth -> addAuthentication(fileRequesterBuilder, repository, auth));

        if (!this.skipCache) {
            fileRequesterBuilder.withCacheDir(this.cacheDirectory);
        }

        final HttpFileRequester fileRequester = fileRequesterBuilder
                .withProgressReport(this.session.getSettings().isInteractiveMode()
                        ? new LoggingProgressReport(this.getLog())
                        : new SilentProgressReport(this.getLog()))
                .withConnectTimeout(this.readTimeOut)
                .withSocketTimeout(this.readTimeOut)
                .withUri(this.uri)
                .withUsername(this.username)
                .withPassword(this.password)
                .withServerId(this.serverId)
                .withPreemptiveAuth(this.preemptiveAuth)
                .withMavenSession(this.session)
                .withRedirectsEnabled(this.followRedirects)
                .withLog(this.getLog())
                .build();
        fileRequester.download(outputFile, getAdditionalHeaders());
    }

    private void addProxy(final HttpFileRequester.Builder fileRequesterBuilder,
                          final RemoteRepository repository,
                          final Proxy proxy) {
        fileRequesterBuilder.withProxyHost(proxy.getHost());
        fileRequesterBuilder.withProxyPort(proxy.getPort());

        final RemoteRepository proxyRepo = new RemoteRepository.Builder(repository)
                .setProxy(proxy)
                .build();

        try ( final AuthenticationContext ctx = AuthenticationContext.forProxy(this.session.getRepositorySession(),
                proxyRepo) ) {
            fileRequesterBuilder.withProxyUserName(ctx.get(AuthenticationContext.USERNAME));
            fileRequesterBuilder.withProxyPassword(ctx.get(AuthenticationContext.PASSWORD));
            fileRequesterBuilder.withNtlmDomain(ctx.get(AuthenticationContext.NTLM_DOMAIN));
            fileRequesterBuilder.withNtlmHost(ctx.get(AuthenticationContext.NTLM_WORKSTATION));
        }
    }

    private void addAuthentication(final HttpFileRequester.Builder fileRequesterBuilder,
                                   final RemoteRepository repository,
                                   final Authentication authentication) {
        final RemoteRepository authRepo = new RemoteRepository.Builder(repository)
                .setAuthentication(authentication)
                .build();
        try (final AuthenticationContext authCtx = AuthenticationContext.forRepository(
                this.session.getRepositorySession(),
                authRepo)) {
            final String username = authCtx.get(AuthenticationContext.USERNAME);
            final String password = authCtx.get(AuthenticationContext.PASSWORD);
            final String ntlmDomain = authCtx.get(AuthenticationContext.NTLM_DOMAIN);
            final String ntlmHost = authCtx.get(AuthenticationContext.NTLM_WORKSTATION);

            getLog().debug("providing custom authentication");
            getLog().debug("username: " + username + " and password: ***");

            fileRequesterBuilder.withUsername(username);
            fileRequesterBuilder.withPassword(password);
            fileRequesterBuilder.withNtlmDomain(ntlmDomain);
            fileRequesterBuilder.withNtlmHost(ntlmHost);
        }
    }

    private List<Header> getAdditionalHeaders() {
        return headers.entrySet().stream()
                .map(pair -> new BasicHeader(pair.getKey(), pair.getValue()))
                .collect(Collectors.toList());
    }
}
