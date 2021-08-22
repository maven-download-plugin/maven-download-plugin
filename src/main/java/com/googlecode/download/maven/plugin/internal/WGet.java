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

import com.googlecode.download.maven.plugin.internal.cache.DownloadCache;
import com.googlecode.download.maven.plugin.internal.checksum.Checksums;
import java.io.File;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyUtils;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.bzip2.BZip2UnArchiver;
import org.codehaus.plexus.archiver.gzip.GZipUnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.snappy.SnappyUnArchiver;
import org.codehaus.plexus.archiver.xz.XZUnArchiver;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

/**
 * Will download a file from a web site using the standard HTTP protocol.
 *
 * @author Marc-Andre Houle
 * @author Mickael Istria (Red Hat Inc)
 */
@Mojo(name = "wget", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = true, threadSafe = true)
public class WGet extends AbstractMojo {

    private static final PoolingHttpClientConnectionManager CONN_POOL;
    /**
     * A map of file caches by their location paths.
     * Ensures one cache instance per path and enables safe execution in parallel
     * builds against the same cache.
     */
    private static final Map<String, DownloadCache> DOWNLOAD_CACHES = new ConcurrentHashMap<>();
    /**
     * A map of file locks by files to be downloaded.
     * Ensures exclusive access to a target file.
     */
    private static final Map<String, Lock> FILE_LOCKS = new ConcurrentHashMap<>();

    static {
        CONN_POOL = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", new SSLConnectionSocketFactory(
                                SSLContexts.createSystemDefault(),
                                SSLProtocols.supported(),
                                null,
                                SSLConnectionSocketFactory.getDefaultHostnameVerifier()))
                        .build(),
                null,
                null,
                null,
                1,
                TimeUnit.MINUTES);
    }

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
    @Parameter(defaultValue = "2")
    private int retries;

    /**
     * Read timeout for a download in milliseconds
     */
    @Parameter(defaultValue = "0")
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
     * Whether to follow redirects (302)
     */
    @Parameter(property = "download.plugin.followRedirects", defaultValue = "false")
    private boolean followRedirects;

    /**
     * A list of additional HTTP headers to send with the request
     */
    @Parameter(property = "download.plugin.headers")
    private Map<String, String> headers = new HashMap<>();

    @Parameter(property = "session", readonly = true)
    private MavenSession session;

    @Parameter(property = "project", readonly = true)
    private MavenProject project;

    @Component
    private ArchiverManager archiverManager;

    /**
     * For transfers
     */
    @Component
    private WagonManager wagonManager;

    @Component
    private BuildContext buildContext;

    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    /**
     * Maven Security Dispatcher
     */
    @Component(hint = "mng-4384")
    private SecDispatcher securityDispatcher;

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

        if (runOnlyAtRoot && !project.isExecutionRoot()) {
            getLog().info("maven-download-plugin:wget skipped (not project root)");
            return;
        }

        if (StringUtils.isNotBlank(serverId) && (StringUtils.isNotBlank(username) || StringUtils.isNotBlank(password))) {
            throw new MojoExecutionException("Specify either serverId or username/password, not both");
        }

        if (settings == null) {
            getLog().warn("settings is null");
        }
        if (this.settings.isOffline()) {
            getLog().debug("maven-download-plugin:wget offline mode");
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
        final DownloadCache cache = DOWNLOAD_CACHES.computeIfAbsent(
            cacheDirectory.getAbsolutePath(),
            directory -> new DownloadCache(new File(directory))
        );
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
                        getLog().warn("The local version of file " + outputFile.getName() + " doesn't match the expected checksum. " +
                            "You should consider checking the specified checksum is correctly set.");
                        checksumMatch = false;
                    }
                }
                if (!checksumMatch || overwrite) {
                    outputFile.delete();
                    haveFile = false;
                } else {
                    getLog().info("File already exist, skipping");
                }
            }

            if (!haveFile) {
                File cached = cache.getArtifact(this.uri, checksums);
                if (!this.skipCache && cached != null && cached.exists()) {
                    getLog().debug("Got from cache: " + cached.getAbsolutePath());
                    Files.copy(cached.toPath(), outputFile.toPath());
                } else {
                    if (this.settings.isOffline()) {
                        if (this.failOnError) {
                            throw new MojoExecutionException("No file in cache and maven is in offline mode");
                        } else {
                            getLog().warn("Ignoring download failure.");
                        }
                    }
                    boolean done = false;
                    while (!done && this.retries > 0) {
                        try {
                            this.doGet(outputFile);
                            checksums.validate(outputFile);
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
                        if (this.failOnError) {
                            throw new MojoFailureException("Could not get content");
                        } else {
                            getLog().warn("Ignoring download failure.");
                            return;
                        }
                    }
                }
            }
            cache.install(this.uri, outputFile, checksums);
            if (this.unpack) {
                unpack(outputFile);
                buildContext.refresh(outputDirectory);
            } else {
            	buildContext.refresh(outputFile);
            }
        } catch (final Exception ex) {
            throw new MojoExecutionException("IO Error", ex);
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
            unarchiver.setDestFile(new File(this.outputDirectory, outputFileName.substring(0, outputFileName.lastIndexOf('.'))));
        } else {
            unarchiver.setDestDirectory(this.outputDirectory);
        }
        unarchiver.extract();
        outputFile.delete();
    }

    private boolean isFileUnArchiver(final UnArchiver unarchiver) {
        return unarchiver instanceof  BZip2UnArchiver ||
                unarchiver instanceof GZipUnArchiver ||
                unarchiver instanceof SnappyUnArchiver ||
                unarchiver instanceof XZUnArchiver;
    }


    private void doGet(final File outputFile) throws Exception {
        final RequestConfig requestConfig;
        if (readTimeOut > 0) {
            getLog().info(
                String.format(
                    "Read Timeout is set to %d milliseconds (apprx %d minutes)",
                    readTimeOut,
                    Math.round(readTimeOut * 1.66667e-5)
                )
            );
            requestConfig = RequestConfig.custom()
                    .setConnectTimeout(readTimeOut)
                    .setSocketTimeout(readTimeOut)
                    .setRedirectsEnabled(followRedirects)
                    .build();
        } else {
            requestConfig = RequestConfig.DEFAULT;
        }

        CredentialsProvider credentialsProvider = null;
        if (StringUtils.isNotBlank(username)) {
            getLog().debug("providing custom authentication");
            getLog().debug("username: " + username + " and password: ***");

            credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(this.uri.getHost(), this.uri.getPort()),
                    new UsernamePasswordCredentials(username, password));

        } else if (StringUtils.isNotBlank(serverId)) {
            getLog().debug("providing custom authentication for " + serverId);
            Server server = settings.getServer(serverId);
            if (server == null) {
                throw new MojoExecutionException(String.format("Server %s not found", serverId));
            }
            getLog().debug(String.format("serverId %s supplies username: %s and password: ***",  serverId, server.getUsername() ));

            credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(this.uri.getHost(), this.uri.getPort()),
                    new UsernamePasswordCredentials(server.getUsername(), decrypt(server.getPassword(), serverId)));

        }

        final HttpRoutePlanner routePlanner;
        final ProxyInfo proxyInfo = this.wagonManager.getProxy(this.uri.getScheme());
        if (this.useHttpProxy(proxyInfo)) {
            routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxyInfo.getHost(), proxyInfo.getPort()));
            if (proxyInfo.getUserName() != null) {
                final Credentials creds;
                if (proxyInfo.getNtlmHost() != null || proxyInfo.getNtlmDomain() != null) {
                    creds = new NTCredentials(proxyInfo.getUserName(),
                            proxyInfo.getPassword(),
                            proxyInfo.getNtlmHost(),
                            proxyInfo.getNtlmDomain());
                } else {
                    creds = new UsernamePasswordCredentials(proxyInfo.getUserName(),
                            proxyInfo.getPassword());
                }
                AuthScope authScope = new AuthScope(proxyInfo.getHost(), proxyInfo.getPort());
                if (credentialsProvider == null) {
                    credentialsProvider = new BasicCredentialsProvider();
                }
                credentialsProvider.setCredentials(authScope, creds);
            }
        } else {
            routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
        }

        try (final CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(CONN_POOL)
                .setConnectionManagerShared(true)
                .setRoutePlanner(routePlanner)
                .build()) {
            final HttpFileRequester fileRequester = new HttpFileRequester(
                httpClient,
                this.session.getSettings().isInteractiveMode() ?
                    new LoggingProgressReport(getLog()) : new SilentProgressReport(getLog()));

            final HttpClientContext clientContext = HttpClientContext.create();
            clientContext.setRequestConfig(requestConfig);
            if (credentialsProvider != null) {
                clientContext.setCredentialsProvider(credentialsProvider);
            }
            fileRequester.download(this.uri, outputFile, clientContext, getAdditionalHeaders());
        }
    }

    private List<Header> getAdditionalHeaders() {
        return headers.entrySet().stream()
                .map(pair -> new BasicHeader(pair.getKey(), pair.getValue()))
                .collect(Collectors.toList());
    }

    private String decrypt(String str, String server) {
        try  {
            return securityDispatcher.decrypt(str);
        } catch(final SecDispatcherException e) {
            getLog().warn(
                String.format(
                    "Failed to decrypt password/passphrase for server %s, using auth token as is",
                    server
                ), e
            );
            return str;
        }
    }

    /**
     * Check if target host should be accessed via proxy.
     * @param proxyInfo Proxy info to check for proxy config.
     * @return True if the target host will be requested via a proxy.
     */
    private boolean useHttpProxy(final ProxyInfo proxyInfo) {
        final boolean result;
        if (proxyInfo == null) {
            result = false;
        } else {
            if (proxyInfo.getHost() == null) {
                result = false;
            } else {
                if (proxyInfo.getNonProxyHosts() == null) {
                    result = true;
                    getLog().debug(
                        String.format("%s is a proxy host", this.uri.getHost())
                    );
                } else {
                    result = !ProxyUtils.validateNonProxyHosts(proxyInfo, this.uri.getHost());
                    getLog().debug(
                        String.format("%s is a non-proxy host", this.uri.getHost())
                    );
                }
            }
        }
        return result;
    }
}
