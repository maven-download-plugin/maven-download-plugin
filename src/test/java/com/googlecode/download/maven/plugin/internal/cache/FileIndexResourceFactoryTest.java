package com.googlecode.download.maven.plugin.internal.cache;

import org.junit.Test;

import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Unit tests for {@linkplain FileIndexResourceFactory}
 */
public class FileIndexResourceFactoryTest {

    /**
     * URI is a relative URI without protocol spec, so it doesn't contain the host name.
     * We will test if the method {@link FileIndexResourceFactory#generateUniqueCachePath(String)}
     * is able to generate a unique path for the root resource.
     */
    @Test
    public void testGenerateUniqueCacheFileForRootResource()
    {
        FileIndexResourceFactory factory = new FileIndexResourceFactory(Paths.get("/tmp"));
        assertThat(factory.generateUniqueCachePath("/").toString(),
                not(containsString("_")));
    }

    /**
     * URI is a relative URI without protocol spec, so it doesn't contain the host name.
     * We will test if the method {@link FileIndexResourceFactory#generateUniqueCachePath(String)}
     * is able to generate a unique path for a resource containing a file name.
     */
    @Test
    public void testGenerateUniqueCacheFileForNonRootResource()
    {
        FileIndexResourceFactory factory = new FileIndexResourceFactory(Paths.get("/tmp"));
        assertThat(factory.generateUniqueCachePath("/someFileName").toString(),
                containsString("someFileName_"));
    }
}
