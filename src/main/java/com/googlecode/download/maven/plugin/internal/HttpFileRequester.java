/*
 * Copyright 2009-2018 The Apache Software Foundation
 *
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;
import org.apache.maven.shared.utils.StringUtils;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

/**
 * File requester that can download resources over HTTP transport using Apache HttpClient 4.x.
 * Use {@link HttpFileRequester.Builder} to create an instance.
 */
@SuppressWarnings(
    {"checkstyle:JavadocVariable", "checkstyle:EmptyLineSeparator", "checkstyle:HiddenField"}
)
public final class HttpFileRequester {

    private static final int MOVED_PERMANENTLY = 301;
    private static final int SEE_OTHER = 303;
    private static final int BAD_REQUEST = 400;

    /**
     * Buffer size used for HTTP file download operations within {@link HttpFileRequester}.
     */
    private static final int BUFFER_SIZE = 8 * 11024;

    private ProgressReport progressReport;
    private int connectTimeout;
    private int socketTimeout;
    private HttpRoutePlanner routePlanner;
    private CredentialsProvider credentialsProvider;
    private boolean redirectsEnabled;
    private URI uri;
    private boolean preemptiveAuth;
    private boolean insecure;

    /**
     * Private constructor.
     */
    private HttpFileRequester() {
    }

    /**
     * Downloads the resource with the given URI to the specified local file system location.
     * @param outputFile The output file.
     * @param headers List of headers.
     */
    public void download(final File outputFile, final List<Header> headers) throws IOException {
        try (CloseableHttpClient httpClient = this.createHttpClientBuilder().build()) {
            final HttpCacheContext clientContext = HttpCacheContext.create();
            clientContext.setCredentialsProvider(this.credentialsProvider);
            if (this.preemptiveAuth) {
                final AuthCache authCache = new BasicAuthCache();
                authCache.put(
                    new HttpHost(this.uri.getHost(), this.uri.getPort()), new BasicScheme());
                clientContext.setAuthCache(authCache);
            }
            final HttpGet httpGet = new HttpGet(this.uri);
            headers.forEach(httpGet::setHeader);
            httpClient.execute(
                httpGet,
                response -> this.handleResponse(this.uri, outputFile, response),
                clientContext
            );
        }
    }

    /**
     * Handles response from the server.
     * @param uri Request uri.
     * @param outputFile Output file for the download request.
     * @param response Response from the server.
     * @return Original Response object.
     * @throws IOException Thrown if I/O operations don't succeed
     */
    private Object handleResponse(final URI uri, final File outputFile, final HttpResponse response)
        throws IOException {
        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= BAD_REQUEST) {
            throw new DownloadFailureException(
                statusCode,
                response.getStatusLine().getReasonPhrase()
            );
        }
        if (statusCode >= MOVED_PERMANENTLY && statusCode <= SEE_OTHER) {
            throw new DownloadFailureException(
                statusCode,
                String.format(
                    "%s, Not downloading the resource because followRedirects is false.",
                    response.getStatusLine().getReasonPhrase()
                )
            );
        }
        final HttpEntity entity = response.getEntity();
        if (entity != null) {
            this.progressReport.initiate(uri, entity.getContentLength());
            try (
                InputStream in = entity.getContent(); OutputStream out =
                    Files.newOutputStream(outputFile.toPath())
            ) {
                final byte[] tmp = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(tmp)) != -1) {
                    out.write(tmp, 0, bytesRead);
                    this.progressReport.update(bytesRead);
                }
                out.flush();
                this.progressReport.completed();
            } catch (final IOException ex) {
                this.progressReport.error(ex);
                throw ex;
            }
        }
        return entity;
    }

    /**
     * Creates and configures an instance of HttpClientBuilder.
     * @return A configured HttpClientBuilder instance
     */
    private HttpClientBuilder createHttpClientBuilder() {
        final HttpClientBuilder httpClientBuilder = HttpClients.custom()
            .setDefaultCredentialsProvider(this.credentialsProvider)
            .setRoutePlanner(this.routePlanner)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(this.connectTimeout)
                .setSocketTimeout(this.socketTimeout)
                .setRedirectsEnabled(this.redirectsEnabled)
                .build());
        if (this.insecure) {
            try {
                httpClientBuilder.setSSLContext(
                        new SSLContextBuilder()
                            .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                            .build())
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
            } catch (final NoSuchAlgorithmException | KeyStoreException
                           | KeyManagementException cantHappen) {
                throw new RuntimeException(cantHappen);
            }
        }
        return httpClientBuilder;
    }

    /**
     * Builder class for creating an instance of HttpFileRequester.
     */
    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MagicNumber"})
    public static final class Builder {
        private ProgressReport progressReport;
        private int connectTimeout = 3000;
        private int socketTimeout = 3000;
        private URI uri;
        private String username;
        private String password;
        private String serverId;
        private String proxyHost;
        private int proxyPort;
        private String proxyUserName;
        private String proxyPassword;
        private String proxyNtlmHost;
        private String proxyNtlmDomain;
        private Log log;
        private boolean redirectsEnabled;
        private MavenSession mavenSession;
        private SecDispatcher secDispatcher;
        private boolean preemptiveAuth;
        private boolean insecure;

        public HttpFileRequester.Builder withUri(final URI uri) {
            this.uri = uri;
            return this;
        }

        public HttpFileRequester.Builder withProgressReport(final ProgressReport progressReport) {
            this.progressReport = progressReport;
            return this;
        }

        public HttpFileRequester.Builder withConnectTimeout(final int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public HttpFileRequester.Builder withSocketTimeout(final int socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public HttpFileRequester.Builder withUsername(final String username) {
            this.username = username;
            return this;
        }

        public HttpFileRequester.Builder withPassword(final String password) {
            this.password = password;
            return this;
        }

        public HttpFileRequester.Builder withServerId(final String serverId) {
            this.serverId = serverId;
            return this;
        }

        public HttpFileRequester.Builder withProxyHost(final String proxyHost) {
            this.proxyHost = proxyHost;
            return this;
        }

        public HttpFileRequester.Builder withProxyPort(final int proxyPort) {
            this.proxyPort = proxyPort;
            return this;
        }

        public HttpFileRequester.Builder withProxyUserName(final String proxyUserName) {
            this.proxyUserName = proxyUserName;
            return this;
        }

        public HttpFileRequester.Builder withProxyPassword(final String proxyPassword) {
            this.proxyPassword = proxyPassword;
            return this;
        }

        public HttpFileRequester.Builder withNtlmHost(final String proxyNtlmHost) {
            this.proxyNtlmHost = proxyNtlmHost;
            return this;
        }

        public HttpFileRequester.Builder withNtlmDomain(final String proxyNtlmDomain) {
            this.proxyNtlmDomain = proxyNtlmDomain;
            return this;
        }

        public HttpFileRequester.Builder withLog(final Log log) {
            this.log = log;
            return this;
        }

        public HttpFileRequester.Builder withRedirectsEnabled(final boolean followRedirects) {
            this.redirectsEnabled = followRedirects;
            return this;
        }

        public HttpFileRequester.Builder withPreemptiveAuth(final boolean preemptiveAuth) {
            this.preemptiveAuth = preemptiveAuth;
            return this;
        }

        public HttpFileRequester.Builder withMavenSession(final MavenSession mavenSession) {
            this.mavenSession = mavenSession;
            return this;
        }

        public HttpFileRequester.Builder withSecDispatcher(final SecDispatcher secDispatcher) {
            this.secDispatcher = secDispatcher;
            return this;
        }

        public HttpFileRequester.Builder withInsecure(final boolean insecure) {
            this.insecure = insecure;
            return this;
        }

        /**
         * Builds an instance of {@code HttpFileRequester} using the configured properties.
         * @return A newly constructed {@code HttpFileRequester}
         * @throws MojoExecutionException If the server with specified ID is not found
         *  or if any other error occurs during the build process.
         */
        @SuppressWarnings({"checkstyle:ExecutableStatementCount", "checkstyle:NestedIfDepth"})
        public HttpFileRequester build() throws MojoExecutionException {
            final HttpFileRequester instance = new HttpFileRequester();
            instance.uri = Objects.requireNonNull(this.uri);
            instance.progressReport = this.progressReport;
            instance.connectTimeout = this.connectTimeout;
            instance.socketTimeout = this.socketTimeout;
            instance.redirectsEnabled = this.redirectsEnabled;
            instance.preemptiveAuth = this.preemptiveAuth;
            instance.insecure = this.insecure;
            instance.credentialsProvider = new BasicCredentialsProvider();
            if (StringUtils.isNotBlank(this.serverId)) {
                Objects.requireNonNull(this.mavenSession);
                if (this.log.isDebugEnabled()) {
                    this.log.debug(
                        String.format("providing custom authentication for %s", this.serverId)
                    );
                }
                final Server server = this.mavenSession.getSettings().getServer(this.serverId);
                if (server == null) {
                    throw new MojoExecutionException(
                        String.format("Server %s not found", this.serverId)
                    );
                }
                if (this.log.isDebugEnabled()) {
                    this.log.debug(
                        String.format("serverId %s supplies username: %s and password: ***",
                            this.serverId, server.getUsername()
                        )
                    );
                }
                instance.credentialsProvider.setCredentials(
                    new AuthScope(this.uri.getHost(), this.uri.getPort()),
                    new UsernamePasswordCredentials(
                        server.getUsername(), this.decrypt(server.getPassword(), this.serverId)
                    )
                );
            } else if (StringUtils.isNotBlank(this.username)) {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("providing custom authentication");
                    this.log.debug(String.format("username: %s and password: ***", this.username));
                }
                instance.credentialsProvider.setCredentials(
                    new AuthScope(this.uri.getHost(), this.uri.getPort()),
                    new UsernamePasswordCredentials(this.username, this.password)
                );
            }
            if (StringUtils.isNotBlank(this.proxyHost)) {
                final HttpHost host = new HttpHost(this.proxyHost, this.proxyPort);
                instance.routePlanner = new DefaultProxyRoutePlanner(host);
                final boolean isProxyAuth = StringUtils.isNotBlank(this.proxyUserName)
                    && StringUtils.isNotBlank(this.proxyPassword);
                if (isProxyAuth) {
                    final Credentials credentials;
                    final boolean isNtlmProxy = StringUtils.isNotBlank(this.proxyNtlmHost)
                        && StringUtils.isNotBlank(this.proxyNtlmDomain);
                    if (isNtlmProxy) {
                        credentials = new NTCredentials(
                            this.proxyUserName, this.proxyPassword,
                            this.proxyNtlmHost, this.proxyNtlmDomain
                        );
                    } else {
                        credentials = new UsernamePasswordCredentials(
                            this.proxyUserName, this.proxyPassword
                        );
                    }
                    instance.credentialsProvider.setCredentials(
                        new AuthScope(host.getHostName(), host.getPort()), credentials
                    );
                }
            } else {
                instance.routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
            }
            return instance;
        }

        /**
         * Decrypt given password using maven security dispatcher.
         * @param password Encrypted password/passphrase.
         * @param server Server ID from settings.xml the password came from.
         * @return Decrypted password or input string if failed to decrypt.
         */
        private String decrypt(final String password, final String server) {
            String result;
            try {
                result = this.secDispatcher.decrypt(password);
            } catch (final SecDispatcherException ex) {
                this.log.warn(
                    String.format(
                        "Failed to decrypt password/passphrase for server %s, %s",
                        server, "using auth token as is"
                    ), ex
                );
                result = password;
            }
            return result;
        }
    }
}
