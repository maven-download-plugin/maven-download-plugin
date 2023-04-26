package com.googlecode.download.maven.plugin.internal;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.auth.AUTH;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link HttpFileRequester}
 *
 * @author Andrzej Jarmoniuk
 */
public class HttpFileRequesterTest {
    @Rule
    public TemporaryFolder outputDirectory = new TemporaryFolder();
    @Rule
    public WireMockRule wireMock = new WireMockRule(options().dynamicPort());
    private File outputFile;
    private final static Log LOG = new SystemStreamLog();
    private final static String OUTPUT_FILE_NAME = "output-file";

    @Before
    public void setUp() throws Exception {
        this.outputFile = new File(this.outputDirectory.getRoot(), OUTPUT_FILE_NAME);
    }

    private HttpFileRequester.Builder createFileRequesterBuilder() throws Exception {
        class MavenSessionStub extends MavenSession {
            @SuppressWarnings("deprecation")
            MavenSessionStub() {
                super(null, mock(MavenExecutionRequest.class), null, new LinkedList<>());
            }
        }

        return new HttpFileRequester.Builder()
                .withProgressReport(new LoggingProgressReport(LOG))
                .withConnectTimeout(3000)
                .withSocketTimeout(3000)
                .withUri(new URI(this.wireMock.baseUrl()))
                .withRedirectsEnabled(false)
                .withPreemptiveAuth(false)
                .withCacheDir(null)
                .withLog(LOG)
                .withMavenSession(new MavenSessionStub());
    }

    /**
     * Tests {@link HttpFileRequester#download(File, List)} with no authentication
     * @throws Exception thrown if {@link HttpFileRequester} creation fails
     */
    @Test
    public void testNoAuth()
            throws Exception {
        this.wireMock.stubFor(get(anyUrl())
                .willReturn(ok().withBody("Hello, world!")));

        createFileRequesterBuilder()
                .build()
                .download(this.outputFile, emptyList());

        assertThat(String.join("", Files.readAllLines(this.outputFile.toPath())),
                is("Hello, world!"));
    }

    /**
     * Tests {@link HttpFileRequester#download(File, List)} with basic authentication
     * @throws Exception thrown if {@link HttpFileRequester} creation fails
     */
    @Test
    public void testBasicAuth()
            throws Exception {
        this.wireMock.stubFor(get(anyUrl())
                .willReturn(unauthorized()
                        .withHeader(AUTH.WWW_AUTH,"Basic")));
        this.wireMock.stubFor(get(anyUrl())
                .withBasicAuth("billg", "hunter2")
                .willReturn(ok().withBody("Hello, world!")));

        createFileRequesterBuilder()
                .withUsername("billg")
                .withPassword("hunter2")
                .build()
                .download(this.outputFile, emptyList());

        assertThat(String.join("", Files.readAllLines(this.outputFile.toPath())),
                is("Hello, world!"));
    }

    /**
     * Tests {@link HttpFileRequester#download(File, List)} with preemptive basic authentication
     * @throws Exception thrown if {@link HttpFileRequester} creation fails
     */
    @Test
    public void testBasicAuthPreempt()
            throws Exception {
        wireMock.stubFor(get(anyUrl())
                .withBasicAuth("billg", "hunter2")
                .willReturn(ok().withBody("Hello, world!")));

        createFileRequesterBuilder()
                .withUsername("billg")
                .withPassword("hunter2")
                .withPreemptiveAuth(true)
                .build()
                .download(this.outputFile, emptyList());

        assertThat(String.join("", Files.readAllLines(this.outputFile.toPath())),
                is("Hello, world!"));
    }

    /**
     * Tests {@link HttpFileRequester#download(File, List)} should throw a {@link DownloadFailureException}
     * if the download fails
     */
    @Test
    public void testDownloadFailure() {
        this.wireMock.stubFor(get(anyUrl())
                .willReturn(forbidden()));

        try {
            createFileRequesterBuilder()
                    .build()
                    .download(this.outputFile, emptyList());
            fail("A DownloadFailureException should have been thrown");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(DownloadFailureException.class)));
        }
    }
}
