package com.googlecode.download.maven.plugin.internal.httpclient;

import org.junit.Test;

import java.nio.file.Path;
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
        assertThat(factory.generateUniqueCacheFile("https://www.test.com/somefile.bin").toString(),
                containsString("www.test.com_"));
        assertThat(factory.generateUniqueCacheFile("www.test.com/somefile.bin").toString(),
                containsString("www.test.com_"));
    }
}
