package com.googlecode.download.maven.plugin.internal.cache;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.message.BasicStatusLine;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;
import wiremock.org.eclipse.jetty.http.HttpStatus;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Unit tests for {@linkplain FileBackedIndex}
 */
public class FileBackedIndexTest {

    @Test
    public void testAsUri() {
        assertThat(FileBackedIndex.asUri("foo://test/file.bin"), is(URI.create("foo://test/file.bin")));
    }

    @Test
    public void testUriWithPortAsUri() {
        assertThat(FileBackedIndex.asUri("bar://test:8080/file.bin"), is(URI.create("bar://test/file.bin")));
    }

    @Test
    public void testUriWithAuthInfoAsUri() {
        assertThat(FileBackedIndex.asUri("test://bill@test:8080/file.bin"), is(URI.create("test://bill@test/file.bin")));
    }

    @Test
    public void testUriWithAdditionalHeaderInfoUri() {
        assertThat(FileBackedIndex.asUri("{Accept-Encoding=gzip%2Cdeflate}https://postman-echo.com:443/get"), is(URI.create("https://postman-echo.com/get")));
    }

    /**
     * Cache should check if the file mapped by the index has been deleted by the user and
     * remove the file from the index reverting to retrieving the remote resource.
     */
    @Test
    public void testIndexedFileNotFound() throws Exception {
        class ClosablePath implements AutoCloseable {
            private final Path dir;
            public ClosablePath(Path dir) {
                this.dir = dir;
            }

            @Override
            public void close() throws Exception {
                FileUtils.deleteDirectory(dir.toFile());
            }
        }

        Path path = Files.createTempDirectory("file-backed-index");
        try (AutoCloseable ignored = new ClosablePath(path)){
            FileBackedIndex index = new FileBackedIndex(path, new SystemStreamLog());
            index.putEntry("foo://file.bin",
                    new HttpCacheEntry(new Date(), new Date(),
                            new BasicStatusLine(HttpVersion.HTTP_1_1,
                                    HttpStatus.OK_200, "OK"), new Header[]{},
                            new FileIndexResource(Paths.get("bogus"), path)));
            assertThat(index.getEntry("foo://file.bin"), is(nullValue()));
        }
    }
}
