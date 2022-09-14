package com.googlecode.download.maven.plugin.internal;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Settings;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WGet}
 *
 * @author Andrzej Jarmoniuk
 */
public class WGetTest {
    private File cacheDirectory;

    private File outputFile;

    @Before
    public void setUp() throws Exception {
        cacheDirectory = Files.createTempDirectory("wget-test-").toFile();
        outputFile = File.createTempFile("wget-test-", "");
    }

    protected <T, M extends Mojo> void setVariableValueToObject(M mojo, String variable, T value) {
        try {
            org.apache.maven.plugin.testing.ArtifactStubFactory.setVariableValueToObject(mojo, variable, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private WGet createMojo(Consumer<WGet> initializer) {
        WGet mojo = new WGet();
        BuildContext buildContext = mock(BuildContext.class);
        doNothing().when(buildContext).refresh(any(File.class));

        setVariableValueToObject(mojo, "uri", outputFile.toURI());
        setVariableValueToObject(mojo, "outputDirectory", outputFile.getParentFile());
        setVariableValueToObject(mojo, "cacheDirectory", cacheDirectory);
        setVariableValueToObject(mojo, "wagonManager", mock(WagonManager.class));
        setVariableValueToObject(mojo, "retries", 1);
        setVariableValueToObject(mojo, "settings", new Settings());
        setVariableValueToObject(mojo, "buildContext", buildContext);
        setVariableValueToObject(mojo, "overwrite", true);
        class MavenSessionStub extends MavenSession {
            MavenSessionStub() {
                super(null, mock(MavenExecutionRequest.class), null, new LinkedList<>());
            }
        }
        setVariableValueToObject(mojo, "session", new MavenSessionStub());

        initializer.accept(mojo);
        return mojo;
    }

    @After
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void tearDown() {
        if (cacheDirectory.exists()) {
            stream(Objects.requireNonNull(cacheDirectory.listFiles())).forEach(File::delete);
            cacheDirectory.delete();
        }
    }

    /**
     * Verifies the cache is not used if {@code skipCache} is {@code true}
     */
    @Test
    public void testCacheDirectoryNotCreated()
            throws MojoExecutionException, MojoFailureException {
        WGet mojo = createMojo(m -> setVariableValueToObject(m, "skipCache", true));
        mojo.execute();
        assertThat("Cache directory should remain empty if skipCache is true", cacheDirectory.list(),
                Matchers.emptyArray());
    }

    /**
     * Verifies the exception message should the cache directory not exist.
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    public void testCacheInANonExistingDirectory() throws Exception {
        // mojo.execute() will produce an error since wagonManager remains null
        createMojo(m -> {
            File nonExistingDir = mock(File.class);
            when(nonExistingDir.getAbsolutePath()).thenReturn("/nonExistingDirectory");
            when(nonExistingDir.exists()).thenReturn(false);
            setVariableValueToObject(m, "cacheDirectory", nonExistingDir);
        }).execute();
        assertThat("Cache directory should remain empty if quit abruptly", cacheDirectory.list(),
                Matchers.emptyArray());
    }

    /**
     * Verifies the exception message should the cache directory not exist.
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    public void testCacheInNotADirectory() throws Exception {
        try {
            createMojo(m -> {
                File notADirectory = mock(File.class);
                when(notADirectory.getAbsolutePath()).thenReturn("/nonExistingDirectory");
                when(notADirectory.exists()).thenReturn(true);
                when(notADirectory.isDirectory()).thenReturn(false);
                setVariableValueToObject(m, "cacheDirectory", notADirectory);
            }).execute();
            fail();
        } catch (MojoFailureException e) {
            assertThat(e.getMessage(), containsString("cacheDirectory is not a directory"));
        }
    }

    /**
     * Verifies that the cache directory should remain empty if the {@linkplain WGet#execute()} execution
     * ended abruptly. Does so by keeping {@code wagonManager} {@code null}. As it's being dereferenced,
     * an NPE is raised.
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    public void testCacheNotWrittenToIfFailed() throws Exception {
        createMojo(mojo -> setVariableValueToObject(mojo, "wagonManager", null)).execute();
        assertThat("Cache directory should remain empty if quit abruptly", cacheDirectory.list(),
                Matchers.emptyArray());
    }

    /**
     * Verifies that the same file is read from cache as it was previously been downloaded by another
     * invocation of the mojo.
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testReadingFromCache() throws Exception {
        Mojo firstMojo = createMojo(mojo -> {
        });
        HttpClientBuilder clientBuilder = mockClientBuilder(() ->
                new ByteArrayInputStream("Hello, world!\n".getBytes()));
        try (MockedStatic<HttpClientBuilder> httpClientBuilder = mockStatic(HttpClientBuilder.class)) {
            httpClientBuilder.when(HttpClientBuilder::create).thenReturn(clientBuilder);

            firstMojo.execute();
        }

        // now, let's try to read that from cache
        createMojo(mojo -> setVariableValueToObject(mojo, "overwrite", true)).execute();

        // now, let's read that file
        Files.readAllLines(outputFile.toPath()).forEach(string -> assertThat(string, is("Hello, world!")));
    }

    /**
     * Verifies that a concurrent invocation of two mojos with, the resulting cache index will keep note of both files.
     * Here, one of the
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testCacheRetainingValuesFromTwoConcurrentCalls() throws Exception {
        File secondOutputFile = File.createTempFile("wget-second-", "");
        try {
            WGet firstMojo = createMojo(mojo ->
                    setVariableValueToObject(mojo, "uri", outputFile.toURI()));
            WGet secondMojo = createMojo(mojo ->
                    setVariableValueToObject(mojo, "uri", secondOutputFile.toURI()));

            CountDownLatch firstMojoStarted = new CountDownLatch(1), secondMojoFinished = new CountDownLatch(1);
            Arrays.asList(CompletableFuture.runAsync(() -> {
                        try (MockedStatic<HttpClientBuilder> httpClientBuilder = mockStatic(HttpClientBuilder.class)) {
                            HttpClientBuilder clientBuilder = mockClientBuilder(() -> {
                                firstMojoStarted.countDown();
                                try {
                                    // waiting till we're sure the second mojo has finished
                                    assert secondMojoFinished.await(1, SECONDS);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                return new ByteArrayInputStream("foo\n".getBytes());
                            });
                            httpClientBuilder.when(HttpClientBuilder::create).thenReturn(clientBuilder);
                            firstMojo.execute();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }),
                    CompletableFuture.runAsync(() -> {
                        try (MockedStatic<HttpClientBuilder> httpClientBuilder = mockStatic(HttpClientBuilder.class)) {
                            HttpClientBuilder clientBuilder = mockClientBuilder(() ->
                                    new ByteArrayInputStream("bar\n".getBytes()));
                            httpClientBuilder.when(HttpClientBuilder::create).thenReturn(clientBuilder);
                            // waiting till we're sure the first mojo has started
                            assert firstMojoStarted.await(1, SECONDS);
                            secondMojo.execute();
                            secondMojoFinished.countDown();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })).forEach(CompletableFuture::join);

            // cache should contain both entries
            @SuppressWarnings("unchecked")
            Map<URI, String> index = (Map<URI, String>) new ObjectInputStream(Files.newInputStream(
                    new File(cacheDirectory, "index.ser").toPath())).readObject();
            assertThat(index.entrySet(), hasSize(2));
            assertThat(index.keySet(), containsInAnyOrder(outputFile.toURI(), secondOutputFile.toURI()));
        } finally {
            secondOutputFile.delete();
        }
    }

    private static HttpClientBuilder mockClientBuilder(Supplier<InputStream> supplier) throws IOException {
        // mock http entity
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContentLength()).thenReturn(1L);
        when(entity.getContent()).thenReturn(supplier.get());

        // mock http response
        HttpResponse response = mock(HttpResponse.class);
        when(response.getEntity())
                .thenReturn(entity);

        // mock http client
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        when(httpClient.execute(any(HttpGet.class), any(), any()))
                .then(i -> {
                    ResponseHandler<Void> responseHandler = i.getArgument(1);
                    responseHandler.handleResponse(response);
                    return null;
                });

        // mock client builder
        HttpClientBuilder clientBuilder = mock(HttpClientBuilder.class);
        when(clientBuilder.setConnectionManager(any())).thenReturn(clientBuilder);
        when(clientBuilder.setConnectionManagerShared(anyBoolean())).thenReturn(clientBuilder);
        when(clientBuilder.setRoutePlanner(any())).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(httpClient);
        return clientBuilder;
    }
}
