package com.googlecode.download.maven.plugin.internal;

import org.apache.http.*;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.nio.file.FileVisitResult.CONTINUE;
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
    private Path cacheDirectory;

    private final static String OUTPUT_FILE_NAME = "output-file";
    private Path outputDirectory;

    @Before
    public void setUp() throws Exception {
        cacheDirectory = Files.createTempDirectory("wget-test-cache-");
        outputDirectory = Files.createTempDirectory("wget-test-");
    }

    @After
    public void tearDown() throws IOException {
        for (Path dir : Arrays.asList(cacheDirectory, outputDirectory)) {
            if (Files.exists(dir)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>()
                {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return CONTINUE;
                    }
                });
            }
        }
    }

    private <T, M extends Mojo> void setVariableValueToObject(M mojo, String variable, T value) {
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

        setVariableValueToObject(mojo, "outputFileName", OUTPUT_FILE_NAME);
        setVariableValueToObject(mojo, "outputDirectory", outputDirectory.toFile());
        setVariableValueToObject(mojo, "cacheDirectory", cacheDirectory.toFile());
        setVariableValueToObject(mojo, "wagonManager", mock(WagonManager.class));
        setVariableValueToObject(mojo, "retries", 1);
        setVariableValueToObject(mojo, "settings", new Settings());
        setVariableValueToObject(mojo, "buildContext", buildContext);
        setVariableValueToObject(mojo, "overwrite", true);
        try {
            setVariableValueToObject(mojo, "uri", new URI(
                    "http://test"));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        class MavenSessionStub extends MavenSession {
            @SuppressWarnings("deprecation")
            MavenSessionStub() {
                super(null, mock(MavenExecutionRequest.class), null, new LinkedList<>());
            }
        }
        setVariableValueToObject(mojo, "session", new MavenSessionStub());

        initializer.accept(mojo);
        return mojo;
    }

    /**
     * Verifies the cache is not used if {@code skipCache} is {@code true}
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testCacheDirectoryNotCreated()
            throws MojoExecutionException, MojoFailureException {
        CachingHttpClientBuilder clientBuilder = createClientBuilder(() -> "Hello, world!");
        try (MockedStatic<CachingHttpClientBuilder> httpClientBuilder = mockStatic(CachingHttpClientBuilder.class)) {
            httpClientBuilder.when(CachingHttpClientBuilder::create).thenReturn(clientBuilder);
            createMojo(m -> setVariableValueToObject(m, "skipCache", true)).execute();
        }
        assertThat("Cache directory should remain empty if skipCache is true", cacheDirectory.toFile().list(),
                emptyArray());
    }

    /**
     * Verifies that there is no exception thrown should the cache directory not exist if no file is retrieved
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testCacheInANonExistingDirectory() throws Exception {
        Path cacheDir = this.cacheDirectory.resolve("cache/dir");
        CachingHttpClientBuilder clientBuilder = createClientBuilder(() -> "Hello, world!");
        try (MockedStatic<CachingHttpClientBuilder> builder = mockStatic(CachingHttpClientBuilder.class)) {
            builder.when(CachingHttpClientBuilder::create).thenReturn(clientBuilder);
            createMojo(m -> setVariableValueToObject(m, "cacheDirectory", cacheDir.toFile())).execute();
        } catch (MojoExecutionException | MojoFailureException e) {
            throw new RuntimeException(e);
        } finally {
            assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                    is("Hello, world!"));
        }
    }

    /**
     * Verifies the exception message should the cache directory not a directory.
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
        try {
            createMojo(mojo -> setVariableValueToObject(mojo, "wagonManager", null)).execute();
            fail();
        } catch (MojoExecutionException e) {
            // expected, but can be ignored
        } finally {
            assertThat("Cache directory should remain empty if quit abruptly", cacheDirectory.toFile().list(),
                    emptyArray());
        }
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
        CachingHttpClientBuilder clientBuilder = createClientBuilder(() -> "Hello, world!");

        // first mojo will get a cache miss
        try (MockedStatic<CachingHttpClientBuilder> httpClientBuilder = mockStatic(CachingHttpClientBuilder.class)) {
            httpClientBuilder.when(CachingHttpClientBuilder::create).thenReturn(clientBuilder);
            createMojo(mojo -> {
            }).execute();

        }

        // now, let's try to read that from cache
        try (MockedStatic<CachingHttpClientBuilder> httpClientBuilder = mockStatic(CachingHttpClientBuilder.class)) {
            httpClientBuilder.when(CachingHttpClientBuilder::create).thenReturn(clientBuilder);
            createMojo(mojo -> setVariableValueToObject(mojo, "overwrite", true)).execute();
        }

        // now, let's read that file
        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello, world!"));
    }

    /**
     * Verifies that a concurrent invocation of two mojos with, the resulting cache index will keep note of both files.
     * One of the processes starts first, then gets held up while another process downloads another file. Once
     * the second process finishes, the first process saves its file as well. The test verifies that both files
     * are present in the cache index. Finally, the test also verifies that the content of the cached files matches
     * the "downloaded" content.
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testCacheRetainingValuesFromTwoConcurrentCalls() throws Exception {
        final URI firstMojoUri = URI.create("http://test/foo");
        final URI secondMojoUri = URI.create("http://test/bar");
        WGet firstMojo = createMojo(mojo -> {
            setVariableValueToObject(mojo, "uri", firstMojoUri);
            setVariableValueToObject(mojo, "outputFileName", OUTPUT_FILE_NAME);
        });
        WGet secondMojo = createMojo(mojo -> {
            setVariableValueToObject(mojo, "uri", secondMojoUri);
            setVariableValueToObject(mojo, "outputFileName", "second-output-file");
        });

        CountDownLatch firstMojoStarted = new CountDownLatch(1), secondMojoFinished = new CountDownLatch(1);
        Arrays.asList(CompletableFuture.runAsync(() -> {
                    CachingHttpClientBuilder clientBuilder = createClientBuilder(() -> {
                        firstMojoStarted.countDown();
                        try {
                            // waiting till we're sure the second mojo has finished
                            assert secondMojoFinished.await(1, SECONDS);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return "foo";
                    });
                    try (MockedStatic<CachingHttpClientBuilder> builder = mockStatic(CachingHttpClientBuilder.class)) {
                        builder.when(CachingHttpClientBuilder::create).thenReturn(clientBuilder);
                        firstMojo.execute();
                    } catch (MojoExecutionException | MojoFailureException e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.runAsync(() -> {
                    CachingHttpClientBuilder clientBuilder = createClientBuilder(() -> "bar");
                    try (MockedStatic<CachingHttpClientBuilder> builder = mockStatic(CachingHttpClientBuilder.class)) {
                        builder.when(CachingHttpClientBuilder::create).thenReturn(clientBuilder);
                        // waiting till we're sure the first mojo has started
                        assert firstMojoStarted.await(1, SECONDS);
                        secondMojo.execute();
                        secondMojoFinished.countDown();
                    } catch (MojoExecutionException | MojoFailureException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })).forEach(CompletableFuture::join);

        // cache should contain both entries
        try (ObjectInputStream stream = new ObjectInputStream(Files.newInputStream(
                cacheDirectory.resolve("index.ser")))) {
            @SuppressWarnings("unchecked")
            Map<URI, String> index = (Map<URI, String>) stream.readObject();
            assertThat(index.entrySet(), hasSize(2));
            assertThat(index.keySet(), containsInAnyOrder(firstMojoUri, secondMojoUri));

            assertThat(String.join("", Files.readAllLines(cacheDirectory.resolve(index.get(firstMojoUri)))),
                    is("foo"));

            assertThat(String.join("", Files.readAllLines(cacheDirectory.resolve(index.get(secondMojoUri)))),
                    is("bar"));
        }
    }

    private static CachingHttpClientBuilder createClientBuilder(Supplier<String> contentSupplier) {
        // mock client builder
        CachingHttpClientBuilder clientBuilder = CachingHttpClientBuilder.create();
        clientBuilder.setConnectionManager(new BasicHttpClientConnectionManager() {
            @Override
            public void connect(HttpClientConnection conn, HttpRoute route, int connectTimeout, HttpContext context) {
            }
        });
        clientBuilder.setRequestExecutor(new HttpRequestExecutor() {
            @Override
            protected HttpResponse doSendRequest(HttpRequest request, HttpClientConnection conn, HttpContext context)
                    throws IOException {
                HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "Ok");
                response.setEntity(new StringEntity(contentSupplier.get() + "\n"));
                return response;
            }
        });

        return clientBuilder;
    }
}
