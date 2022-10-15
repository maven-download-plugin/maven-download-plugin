package com.googlecode.download.maven.plugin.internal.cache;

import org.junit.Test;

import java.net.URI;

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
}
