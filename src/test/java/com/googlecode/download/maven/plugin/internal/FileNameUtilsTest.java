package com.googlecode.download.maven.plugin.internal;

import org.junit.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test suite for the {@link FileNameUtils} class
 */
public class FileNameUtilsTest {

    /**
     * Shall return the host name if the resource is empty.
     */
    @Test
    public void testGetOutputFileNameForEmptyResource() {
        assertThat(FileNameUtils.getOutputFileName(URI.create("https://www.dummy.com")), is("www.dummy.com"));
    }

    /**
     * Shall return the host name if the resource is just the root resource.
     */
    @Test
    public void testGetOutputFileNameForRootResource() {
        assertThat(FileNameUtils.getOutputFileName(URI.create("https://www.dummy.com/")), is("www.dummy.com"));
    }

    /**
     * Shall return the resource name if the resource not the root resource
     */
    @Test
    public void testGetOutputFileNameForNonRootResource() {
        assertThat(FileNameUtils.getOutputFileName(URI.create("https://www.dummy.com/resource")), is("resource"));
    }
}
