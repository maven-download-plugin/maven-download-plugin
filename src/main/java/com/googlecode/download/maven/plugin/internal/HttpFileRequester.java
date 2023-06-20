/**
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

import com.googlecode.download.maven.plugin.internal.cache.FileBackedIndex;
import com.googlecode.download.maven.plugin.internal.cache.FileIndexResourceFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.client.cache.CachingHttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;
import static org.apache.maven.shared.utils.StringUtils.isNotBlank;

/**
 * File requester that can download resources over HTTP transport using Apache HttpClient 4.x.
 */
public class HttpFileRequester {
    public static final int HEURISTIC_DEFAULT_LIFETIME = 364 * 3600 * 24;

    private ProgressReport progressReport;
    private int connectTimeout;
    private int socketTimeout;
    private HttpRoutePlanner routePlanner;
    private CredentialsProvider credentialsProvider;
    private File cacheDir;
    private Log log;
    private boolean redirectsEnabled;
    private URI uri;
    private boolean preemptiveAuth;

    private HttpFileRequester() {
    }

    public static class Builder {
        private ProgressReport progressReport;
        private File cacheDir;
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
        private boolean preemptiveAuth;

        public Builder withUri(URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder withProgressReport(ProgressReport progressReport) {
            this.progressReport = progressReport;
            return this;
        }

        public Builder withCacheDir(File cacheDir) {
            this.cacheDir = cacheDir;
            return this;
        }

        public Builder withConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder withSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder withServerId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public Builder withProxyHost(String proxyHost) {
            this.proxyHost = proxyHost;
            return this;
        }

        public Builder withProxyPort(int proxyPort) {
            this.proxyPort = proxyPort;
            return this;
        }

        public Builder withProxyUserName(String proxyUserName) {
            this.proxyUserName = proxyUserName;
            return this;
        }

        public Builder withProxyPassword(String proxyPassword) {
            this.proxyPassword = proxyPassword;
            return this;
        }

        public Builder withNtlmHost(String proxyNtlmHost) {
            this.proxyNtlmHost = proxyNtlmHost;
            return this;
        }

        public Builder withNtlmDomain(String proxyNtlmDomain) {
            this.proxyNtlmDomain = proxyNtlmDomain;
            return this;
        }

        public Builder withLog(Log log) {
            this.log = log;
            return this;
        }

        public Builder withRedirectsEnabled(boolean followRedirects) {
            this.redirectsEnabled = followRedirects;
            return this;
        }

        public Builder withPreemptiveAuth(boolean preemptiveAuth) {
            this.preemptiveAuth = preemptiveAuth;
            return this;
        }

        public Builder withMavenSession(MavenSession mavenSession) {
            this.mavenSession = mavenSession;
            return this;
        }

        public HttpFileRequester build() throws MojoExecutionException {
            final HttpFileRequester instance = new HttpFileRequester();
            instance.uri = requireNonNull(this.uri);
            instance.progressReport = this.progressReport;
            instance.connectTimeout = this.connectTimeout;
            instance.socketTimeout = this.socketTimeout;
            instance.cacheDir = this.cacheDir;
            instance.redirectsEnabled = this.redirectsEnabled;
            instance.preemptiveAuth = this.preemptiveAuth;
            instance.log = requireNonNull(this.log);

            instance.credentialsProvider = new BasicCredentialsProvider();
            if (isNotBlank(this.serverId)) {
                requireNonNull(this.mavenSession);

                if (this.log.isDebugEnabled()) {
                    this.log.debug("providing custom authentication for " + this.serverId);
                }
                final Server server = this.mavenSession.getSettings().getServer(serverId);
                if (server == null) {
                    throw new MojoExecutionException(String.format("Server %s not found", serverId));
                }
                if (this.log.isDebugEnabled()) {
                    this.log.debug(String.format("serverId %s supplies username: %s and password: ***",
                            serverId, server.getUsername()));
                }
                instance.credentialsProvider.setCredentials(
                        new AuthScope(this.uri.getHost(), this.uri.getPort()),
                        new UsernamePasswordCredentials(server.getUsername(), server.getPassword()));
            } else if (isNotBlank(this.username)) {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("providing custom authentication");
                    this.log.debug("username: " + username + " and password: ***");
                }
                instance.credentialsProvider.setCredentials(
                        new AuthScope(this.uri.getHost(), this.uri.getPort()),
                        new UsernamePasswordCredentials(this.username, this.password));
            }

            if (isNotBlank(this.proxyHost)) {
                // TODO: authenticate with the proxy
                final HttpHost host = new HttpHost(this.proxyHost, this.proxyPort);
                instance.routePlanner = new DefaultProxyRoutePlanner(host);
                if (isNotBlank(this.proxyUserName) && isNotBlank(this.proxyPassword)) {
                    instance.credentialsProvider.setCredentials(
                            new AuthScope(host.getHostName(), host.getPort()),
                            isNotBlank(this.proxyNtlmHost) && isNotBlank(this.proxyNtlmDomain)
                                ? new NTCredentials(this.proxyUserName, this.proxyPassword, this.proxyNtlmHost,
                                    this.proxyNtlmDomain)
                                : new UsernamePasswordCredentials(this.proxyUserName, this.proxyPassword));
                }
            } else {
                instance.routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
            }

            return instance;
        }
    }


    /**
     * Downloads the resource with the given URI to the specified local file system location.
     *
     * @param outputFile the output file
     * @param headers list of headers
     */
    public void download(final File outputFile, List<Header> headers) throws IOException {
        final CachingHttpClientBuilder httpClientBuilder = createHttpClientBuilder();
        try (final CloseableHttpClient httpClient = httpClientBuilder.build()) {
            final HttpCacheContext clientContext = HttpCacheContext.create();
            clientContext.setCredentialsProvider(this.credentialsProvider);

            if (this.preemptiveAuth) {
                final AuthCache authCache = new BasicAuthCache();
                authCache.put(new HttpHost(this.uri.getHost(), this.uri.getPort()), new BasicScheme());
                clientContext.setAuthCache(authCache);
            }

            final HttpGet httpGet = new HttpGet(this.uri);
            headers.forEach(httpGet::setHeader);
            httpClient.execute(httpGet, response -> handleResponse(this.uri, outputFile, clientContext, response),
                    clientContext);
        }
    }

    /**
     * Handles response from the server
     * @param uri request uri
     * @param outputFile output file for the download request
     * @param clientContext {@linkplain HttpCacheContext} object
     * @param response response from the server
     * @return original response object
     * @throws IOException thrown if I/O operations don't succeed
     */
    private Object handleResponse( URI uri, File outputFile, HttpCacheContext clientContext, HttpResponse response )
            throws IOException {
        if (response.getStatusLine().getStatusCode() >= 400) {
            throw new DownloadFailureException(response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase());
        }
        if (response.getStatusLine().getStatusCode() >= 301 && response.getStatusLine().getStatusCode() <= 303) {
            throw new DownloadFailureException(response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase()
                            + ". Not downloading the resource because followRedirects is false.");
        }
        final HttpEntity entity = response.getEntity();
        if (entity != null) {
            switch ( clientContext.getCacheResponseStatus()) {
                case CACHE_HIT:
                case CACHE_MODULE_RESPONSE:
                case VALIDATED:
                    log.debug("Copying file from cache");
                    Files.copy(entity.getContent(), outputFile.toPath(), REPLACE_EXISTING);
                    break;
                default:
                    progressReport.initiate( uri, entity.getContentLength());
                    byte[] tmp = new byte[8 * 11024];
                    try (InputStream in = entity.getContent(); OutputStream out =
                            Files.newOutputStream( outputFile.toPath())) {
                        int bytesRead;
                        while ((bytesRead = in.read(tmp)) != -1) {
                            out.write(tmp, 0, bytesRead);
                            progressReport.update(bytesRead);
                        }
                        out.flush();
                        progressReport.completed();

                    } catch (IOException ex) {
                        progressReport.error(ex);
                        throw ex;
                    }
                    break;
            }
        }
        return entity;
    }

    private CachingHttpClientBuilder createHttpClientBuilder() throws NotDirectoryException {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout)
                .setRedirectsEnabled(redirectsEnabled)
                .build();
        final CachingHttpClientBuilder httpClientBuilder =
                (CachingHttpClientBuilder) CachingHttpClients.custom()
                        .setDefaultCredentialsProvider(this.credentialsProvider)
                        .setRoutePlanner(routePlanner)
                        .setDefaultRequestConfig(requestConfig)
                ;
        if (cacheDir != null) {
            CacheConfig config = CacheConfig.custom()
                    .setHeuristicDefaultLifetime(HEURISTIC_DEFAULT_LIFETIME)
                    .setHeuristicCachingEnabled(true)
                    .setMaxObjectSize(Long.MAX_VALUE)
                    .setMaxCacheEntries(Integer.MAX_VALUE)
                    .build();
            httpClientBuilder
                    .setCacheDir(this.cacheDir)
                    .setCacheConfig(config)
                    .setResourceFactory(new FileIndexResourceFactory(this.cacheDir.toPath()))
                    .setHttpCacheStorage(new FileBackedIndex(this.cacheDir.toPath(), this.log))
                    .setDeleteCache(false);
        }

        return httpClientBuilder;
    }
}
