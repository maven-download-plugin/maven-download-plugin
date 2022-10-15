package com.googlecode.download.maven.plugin.internal.cache;

import org.junit.Test;

import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Unit tests for {@linkplain FileIndexResourceFactory}
 */
public class FileIndexResourceFactoryTest {

    @Test
    public void testGenerateUniqueCacheFile()
    {
        FileIndexResourceFactory factory = new FileIndexResourceFactory(Paths.get("/tmp"));
        assertThat(factory.generateUniqueCachePath("dummy://www.test.com/somefile.bin").toString(),
                containsString("www.test.com_"));
        assertThat(factory.generateUniqueCachePath("test://www.test.com:8080/somefile.bin").toString(),
                containsString("www.test.com_"));
        assertThat(factory.generateUniqueCachePath("foo://billg@www.test.com:8080/somefile.bin").toString(),
                containsString("www.test.com_"));
        assertThat(factory.generateUniqueCachePath("bar://billg:hunter2@www.test.com:8080/somefile.bin").toString(),
                containsString("www.test.com_"));
    }
}
