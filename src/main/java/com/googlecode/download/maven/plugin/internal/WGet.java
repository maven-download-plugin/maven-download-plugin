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

import java.io.File;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

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

import com.googlecode.download.maven.plugin.internal.cache.DownloadCache;

/**
 * Will download a file from a web site using the standard HTTP protocol.
 *
 * @author Marc-Andre Houle
 * @author Mickael Istria (Red Hat Inc)
 */
@Mojo(name = "wget", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = true)
public class WGet extends AbstractMojo {

    private static final PoolingHttpClientConnectionManager CONN_POOL;

    static {
        CONN_POOL = new PoolingHttpClientConnectionManager(
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", new SSLConnectionSocketFactory(
                                SSLContexts.createSystemDefault(),
                                new String[] { "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2" },
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
    @Parameter(property = "download.unpack", defaultValue = "false")
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
     * Whether to check the signature of existing files
     */
    @Parameter(property = "checkSignature", defaultValue = "false")
    private boolean checkSignature;

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
     * Method call whent he mojo is executed for the first time.
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
                            doGet(outputFile);
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
                buildContext.refresh(outputDirectory);
            } else {
            	buildContext.refresh(outputFile);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("IO Error", ex);
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
                    "Read Timeout is set to " + readTimeOut + " milliseconds (apprx "
                            + Math.round(readTimeOut * 1.66667e-5) + " minutes)");
            requestConfig = RequestConfig.custom()
                    .setConnectTimeout(readTimeOut)
                    .setSocketTimeout(readTimeOut)
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
        ProxyInfo proxyInfo = this.wagonManager.getProxy(this.uri.getScheme());
        if (proxyInfo != null && proxyInfo.getHost() != null && ProxyInfo.PROXY_HTTP.equals(proxyInfo.getType())) {
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

        final CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(CONN_POOL)
                .setConnectionManagerShared(true)
                .setRoutePlanner(routePlanner)
                .build();

        final HttpFileRequester fileRequester = new HttpFileRequester(
                httpClient,
                this.session.getSettings().isInteractiveMode() ?
                        new LoggingProgressReport(getLog()) : new SilentProgressReport(getLog()));

        final HttpClientContext clientContext = HttpClientContext.create();
        clientContext.setRequestConfig(requestConfig);
        if (credentialsProvider != null) {
            clientContext.setCredentialsProvider(credentialsProvider);
        }

        fileRequester.download(this.uri, outputFile, clientContext);
    }

    private String decrypt(String str, String server) {
        try  {
            return securityDispatcher.decrypt(str);
        }
        catch(SecDispatcherException e) {
            getLog().warn(String.format("Failed to decrypt password/passphrase for server %s, using auth token as is", server), e);
            return str;
        }
    }
}
