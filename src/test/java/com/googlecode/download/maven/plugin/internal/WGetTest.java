package com.googlecode.download.maven.plugin.internal;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.*;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.plexus.util.ReflectionUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WGetMojo}
 *
 * @author Andrzej Jarmoniuk
 */
public class WGetTest {
    @Rule
    public WireMockRule wireMock = new WireMockRule(options().dynamicPort());
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private Path cacheDirectory;
    private final static String OUTPUT_FILE_NAME = "output-file";
    private Path outputDirectory;

    @Before
    public void setUp() throws Exception {
        temporaryFolder.create();
        cacheDirectory = temporaryFolder.newFolder("wget-test-cache").toPath();
        outputDirectory = temporaryFolder.newFolder("wget-test").toPath();
    }

    @After
    public void tearDown() {
        temporaryFolder.delete();
    }

    private <T> void setVariableValueToObject(Object object, String variable, T value) {
        try {
            Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses(variable, object.getClass());
            field.setAccessible(true);
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private WGetMojo createMojo(Consumer<WGetMojo> initializer) {
        WGetMojo mojo = new WGetMojo();
        BuildContext buildContext = mock(BuildContext.class);
        doNothing().when(buildContext).refresh(any(File.class));

        setVariableValueToObject(mojo, "outputFileName", OUTPUT_FILE_NAME);
        setVariableValueToObject(mojo, "outputDirectory", outputDirectory.toFile());
        setVariableValueToObject(mojo, "cacheDirectory", cacheDirectory.toFile());
        setVariableValueToObject(mojo, "retries", 1);
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
                final DefaultRepositorySystemSession repositorySystemSession = new DefaultRepositorySystemSession();
                repositorySystemSession.setOffline(false);
                setVariableValueToObject(this, "repositorySession", repositorySystemSession);
            }
        }
        setVariableValueToObject(mojo, "session", new MavenSessionStub());

        initializer.accept(mojo);
        return mojo;
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

    /**
     * Verifies the cache is not used if {@code skipCache} is {@code true}
     */
    @Test
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
     * Verifies that the cache directory should remain empty if the {@linkplain WGetMojo#execute()} execution
     * ended abruptly. Does so by keeping {@code wagonManager} {@code null}. As it's being dereferenced,
     * an NPE is raised.
     *
     * @throws Exception should any exception be thrown
     */
    @Test
    public void testCacheNotWrittenToIfFailed() throws Exception {
        try {
            WGetMojo mojo = createMojo(ignored -> {
                return;
            });
            // inject null value so that we get a NullPointerException thrown
            setVariableValueToObject(mojo, "session", null);
            mojo.execute();
            fail();
        } catch (NullPointerException e) {
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
    public void testCacheRetainingValuesFromTwoConcurrentCalls() throws Exception {
        final URI firstMojoUri = URI.create("http://test/foo");
        final URI secondMojoUri = URI.create("http://test/bar");
        WGetMojo firstMojo = createMojo(mojo -> {
            setVariableValueToObject(mojo, "uri", firstMojoUri);
            setVariableValueToObject(mojo, "outputFileName", OUTPUT_FILE_NAME);
        });
        WGetMojo secondMojo = createMojo(mojo -> {
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

    /**
     * The plugin should echo headers given in the {@code headers} parameter to the resource.
     */
    @Test
    public void testCustomHeaders() throws MojoExecutionException, MojoFailureException {
        this.wireMock.stubFor(get(anyUrl()).willReturn(serverError()));
        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
            setVariableValueToObject(m, "skipCache", true);
            setVariableValueToObject(m, "headers", new HashMap<String, String>() {{
                put("X-Custom-1", "first custom header");
                put("X-Custom-2", "second custom header");
            }});
        }).execute();
        verify(getRequestedFor(anyUrl())
                .withHeader("X-Custom-1", equalTo("first custom header"))
                .withHeader("X-Custom-2", equalTo("second custom header")));
    }

    /**
     * The plugin should pass query parameters in the URL to the resource
     */
    @Test
    public void testQuery() throws MojoExecutionException, MojoFailureException {
        this.wireMock.stubFor(get(anyUrl()).willReturn(serverError()));
        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl() + "?query=value"));
            setVariableValueToObject(m, "skipCache", true);
        }).execute();
        verify(getRequestedFor(anyUrl())
                .withQueryParam("query", equalTo("value")));
    }

    /**
     * The plugin should follow temporary redirects
     */
    @Test
    public void testTemporaryRedirect() throws MojoExecutionException, MojoFailureException {
        this.wireMock
                .stubFor(get(urlEqualTo("/old"))
                .willReturn(temporaryRedirect("/new")));
        this.wireMock
                .stubFor(get(urlEqualTo("/new"))
                        .willReturn(serverError()));
        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.url("/old")));
            setVariableValueToObject(m, "skipCache", true);
            setVariableValueToObject(m, "followRedirects", true);
        }).execute();
        verify(getRequestedFor(urlEqualTo("/old")));
        verify(getRequestedFor(urlEqualTo("/new")));
    }

    /**
     * The plugin should follow permanent redirects
     */
    @Test
    public void testPermanentRedirect() throws MojoExecutionException, MojoFailureException {
        this.wireMock
                .stubFor(get(urlEqualTo("/old"))
                        .willReturn(permanentRedirect("/new")));
        this.wireMock
                .stubFor(get(urlEqualTo("/new"))
                        .willReturn(serverError()));
        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.url("/old")));
            setVariableValueToObject(m, "skipCache", true);
            setVariableValueToObject(m, "followRedirects", true);
        }).execute();
        verify(getRequestedFor(urlEqualTo("/old")));
        verify(getRequestedFor(urlEqualTo("/new")));
    }

    /**
     * The plugin should always overwrite the output file (even if it has the same content) if {@code overwrite}
     * is {@code true}.
     */
    @Test
    public void testAlwaysOverwrite() throws MojoExecutionException, MojoFailureException, IOException, InterruptedException {
        this.wireMock
                .stubFor(get(anyUrl()).willReturn(ok("Hello")));

        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
            setVariableValueToObject(m, "skipCache", true);
            setVariableValueToObject(m, "overwrite", true);
            setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
        }).execute();

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello"));

        final Path outputPath = outputDirectory.resolve(OUTPUT_FILE_NAME);
        final FileTime firstModificationTime = Files.getLastModifiedTime(outputPath);

        // apparently, Files.getLastModificationTime only offers a resolution up to seconds with Java 8
        // see https://stackoverflow.com/questions/24804618/get-file-mtime-with-millisecond-resolution-from-java
        Thread.sleep(1000);

        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
            setVariableValueToObject(m, "skipCache", true);
            setVariableValueToObject(m, "overwrite", true);
            setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
        }).execute();

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello"));
        assertThat(Files.getLastModifiedTime(outputPath), not(firstModificationTime));
    }

    /**
     * The plugin should always overwrite the output file (even if it has the same content) if {@code overwrite}
     * is {@code true} and {@code skipCache} is {@code true}.
     *
     * @see <a href="https://github.com/maven-download-plugin/maven-download-plugin/issues/194">#194</a>
     */
    @Test
    public void testOverwriteWithSkipCache() throws MojoExecutionException, MojoFailureException, IOException, InterruptedException {
        this.wireMock
                .stubFor(get(anyUrl()).willReturn(ok("Hello")));

        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
            setVariableValueToObject(m, "skipCache", false);
            setVariableValueToObject(m, "overwrite", true);
            setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
        }).execute();

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello"));

        final Path outputPath = outputDirectory.resolve(OUTPUT_FILE_NAME);
        final FileTime firstModificationTime = Files.getLastModifiedTime(outputPath);

        // apparently, Files.getLastModificationTime only offers a resolution up to seconds with Java 8
        // see https://stackoverflow.com/questions/24804618/get-file-mtime-with-millisecond-resolution-from-java
        Thread.sleep(1000);

        createMojo(m -> {
            setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
            setVariableValueToObject(m, "skipCache", true);
            setVariableValueToObject(m, "overwrite", true);
            setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
        }).execute();

        assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                is("Hello"));
        assertThat(Files.getLastModifiedTime(outputPath), not(firstModificationTime));
    }

    /**
     * The plugin, if provided the {@code md5}, {@code sha1}, {@code sha256}, {@code sha512} parameters, should verify
     * if the signature is correct.
     */
    @Test
    public void testSignatures() {
        this.wireMock.stubFor(get(anyUrl()).willReturn(ok("Hello, world!\n")));
        new HashMap<String, String>() {{
            put("md5", "746308829575e17c3331bbcb00c0898b");
            put("sha1", "09fac8dbfd27bd9b4d23a00eb648aa751789536d");
            put("sha256", "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5");
            put("sha512", "09e1e2a84c92b56c8280f4a1203c7cffd61b162cfe987278d4d6be9afbf38c0e"
                    + "8934cdadf83751f4e99d111352bffefc958e5a4852c8a7a29c95742ce59288a8");
        }}.forEach((key, value) -> {
            try {
                createMojo(m -> {
                    setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
                    setVariableValueToObject(m, "skipCache", true);
                    setVariableValueToObject(m, "overwrite", true);
                    setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
                    setVariableValueToObject(m, key, value);
                }).execute();
                assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                        is("Hello, world!"));
            } catch (MojoExecutionException | MojoFailureException | IOException ex) {
                final Throwable rootCause = getRootCause(ex);
                fail("Execution failed with " + key + " test: " + rootCause + " stack trace:\n"
                        + printStackTrace(rootCause));
            }
        });
    }

    /**
     * The plugin, if provided the {@code md5}, {@code sha1}, {@code sha256}, {@code sha512} parameters, should verify
     * if the signature is incorrect.
     */
    @Test
    public void testWrongSignatures() throws MojoExecutionException, MojoFailureException, IOException {
        this.wireMock.stubFor(get(anyUrl()).willReturn(ok("Hello, world!\n")));
        Arrays.stream(new String[]{ "md5", "sha1", "sha256", "sha512" }).forEach(key -> {
            try {
                createMojo(m -> {
                    setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
                    setVariableValueToObject(m, "skipCache", true);
                    setVariableValueToObject(m, "overwrite", true);
                    setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
                    setVariableValueToObject(m, key, "wrong");
                }).execute();
                assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                        is("Hello, world!"));
                fail("Execution failed with " + key + " test: accepted an incorrect signature");
            } catch (MojoExecutionException | MojoFailureException | IOException ex) {
                final Throwable rootCause = getRootCause(ex);
                if (rootCause.getMessage() == null || !rootCause.getMessage().contains("Not same digest as expected")) {
                    fail("Execution failed with " + key + " test: " + rootCause + " stack trace:\n"
                            + printStackTrace(rootCause));
                }
            }
        });
    }
    private static Throwable getRootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static String printStackTrace(Throwable t) {
        return Arrays.stream(t.getStackTrace())
                .map(StackTraceElement::toString)
                .map(s -> "\t" + s)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Testing {@code alwaysVerifyChecksum} with an already existing file. The plugin, if provided the {@code md5},
     * @code sha1}, {@code sha256}, {@code sha512} parameters, should verify if the signature <u>of the existing
     * file</u> is correct.
     */
    @Test
    public void testExistingFileSignatures() {
        Path outputFile = outputDirectory.resolve(OUTPUT_FILE_NAME);
        this.wireMock.stubFor(get(anyUrl()).willReturn(ok("Hello, world!\n")));
        new HashMap<String, String>() {{
            put("md5", "746308829575e17c3331bbcb00c0898b");
            put("sha1", "09fac8dbfd27bd9b4d23a00eb648aa751789536d");
            put("sha256", "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5");
            put("sha512", "09e1e2a84c92b56c8280f4a1203c7cffd61b162cfe987278d4d6be9afbf38c0e"
                    + "8934cdadf83751f4e99d111352bffefc958e5a4852c8a7a29c95742ce59288a8");
        }}.forEach((key, value) -> {
            try {
                Files.write(outputFile, Collections.singletonList("Hello, world!"));
                createMojo(m -> {
                    setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
                    setVariableValueToObject(m, "skipCache", true);
                    setVariableValueToObject(m, "alwaysVerifyChecksum", true);
                    setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
                    setVariableValueToObject(m, key, value);
                }).execute();
                assertThat(String.join("", Files.readAllLines(outputDirectory.resolve(OUTPUT_FILE_NAME))),
                        is("Hello, world!"));
            } catch (MojoExecutionException | MojoFailureException | IOException ex) {
                final Throwable rootCause = getRootCause(ex);
                fail("Execution failed with " + key + " test: " + rootCause + " stack trace:\n"
                        + printStackTrace(rootCause));
            }
        });
    }

    /**
     * Testing {@code alwaysVerifyChecksum} with an already existing file. The plugin, if provided the {@code md5},
     * @code sha1}, {@code sha256}, {@code sha512} parameters, should log a warning message if the signature
     * of the existing file is incorrect.
     */
    @Test
    public void testWrongSignatureOfExistingFile() {
        Path outputFile = outputDirectory.resolve(OUTPUT_FILE_NAME);
        this.wireMock.stubFor(get(anyUrl()).willReturn(ok("Hello, world!\n")));
        Log log = spy(SystemStreamLog.class);
        StringBuilder loggedWarningMessages = new StringBuilder();
        doAnswer(invocation -> loggedWarningMessages.append((CharSequence) invocation.getArgument(0)))
                .when(log).warn(anyString());
        new HashMap<String, String>() {{
            put("md5", "746308829575e17c3331bbcb00c0898b");
            put("sha1", "09fac8dbfd27bd9b4d23a00eb648aa751789536d");
            put("sha256", "d9014c4624844aa5bac314773d6b689ad467fa4e1d1a50a1b8a99d5a95f72ff5");
            put("sha512", "09e1e2a84c92b56c8280f4a1203c7cffd61b162cfe987278d4d6be9afbf38c0e"
                    + "8934cdadf83751f4e99d111352bffefc958e5a4852c8a7a29c95742ce59288a8");
        }}.forEach((key, value) -> {
            try {
                Files.write(outputFile, Collections.singletonList("wrong"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                createMojo(m -> {
                    setVariableValueToObject(m, "uri", URI.create(wireMock.baseUrl()));
                    setVariableValueToObject(m, "skipCache", true);
                    setVariableValueToObject(m, "alwaysVerifyChecksum", true);
                    setVariableValueToObject(m, "outputFileName", OUTPUT_FILE_NAME);
                    setVariableValueToObject(m, "log", log);
                    setVariableValueToObject(m, key, value);
                }).execute();
                assertThat(loggedWarningMessages.toString(),
                        containsString("The local version of file output-file doesn't match the expected checksum."));
            } catch (MojoExecutionException | MojoFailureException | IOException ex) {
                final Throwable rootCause = getRootCause(ex);
                if (rootCause.getMessage() == null || !rootCause.getMessage().contains("Not same digest as expected")) {
                    fail("Execution failed with " + key + " test: " + rootCause + " stack trace:\n"
                            + printStackTrace(rootCause));
                }
            }
        });
    }
}
