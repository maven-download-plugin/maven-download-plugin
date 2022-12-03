package com.googlecode.download.maven.plugin.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.googlecode.download.maven.plugin.test.TestUtils;
import org.apache.http.auth.AUTH;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link HttpFileRequester}
 *
 * @author Andrzej Jarmoniuk
 */
public class HttpFileRequesterTest extends AbstractMojoTestCase {

    private final static Log LOG = new SystemStreamLog();
    private final static String OUTPUT_FILE_NAME = "output-file";
    private Path outputDirectory;
    private WireMockServer wireMock;

    private final static Random RND = new Random();

    @Before
    public void setUp() throws Exception {
        wireMock = new WireMockServer(RND.nextInt(0x7000) + 0x1000);
        wireMock.start();
        outputDirectory = Files.createTempDirectory("http-file-requester-test-");
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        try {
            wireMock.stop();
            TestUtils.tearDownTempDir(outputDirectory);
        }
        finally {
            super.tearDown();
        }
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
                .withUri(new URI(wireMock.baseUrl()))
                .withSecDispatcher(lookup(SecDispatcher.class, "mng-4384"))
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
        wireMock.stubFor(get(anyUrl())
                .willReturn(ok().withBody("Hello, world!")));

        createFileRequesterBuilder()
                .build()
                .download(outputDirectory.resolve(OUTPUT_FILE_NAME).toFile(), emptyList());

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello, world!"));
    }

    /**
     * Tests {@link HttpFileRequester#download(File, List)} with basic authentication
     * @throws Exception thrown if {@link HttpFileRequester} creation fails
     */
    @Test
    public void testBasicAuth()
            throws Exception {
        wireMock.stubFor(get(anyUrl())
                .willReturn(unauthorized()
                        .withHeader(AUTH.WWW_AUTH,"Basic")));
        wireMock.stubFor(get(anyUrl())
                .withBasicAuth("billg", "hunter2")
                .willReturn(ok().withBody("Hello, world!")));

        createFileRequesterBuilder()
                .withUsername("billg")
                .withPassword("hunter2")
                .build()
                .download(outputDirectory.resolve(OUTPUT_FILE_NAME).toFile(), emptyList());

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
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
                .download(outputDirectory.resolve(OUTPUT_FILE_NAME).toFile(), emptyList());

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello, world!"));
    }
}
