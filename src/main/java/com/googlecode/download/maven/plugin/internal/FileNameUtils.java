package com.googlecode.download.maven.plugin.internal;

import java.net.URI;

/**
 * Common utilities used by the {@link WGetMojo}
 */
public final class FileNameUtils {

    /**
     * Attempts to construct the target file name based on an URI as the relative resource name
     * or, if the root resource is requested, the host name extracted from the URI.
     * @param uri uri to extract the output name from
     * @return output file name based on the URI
     */
    public static String getOutputFileName(URI uri) {
        return uri.getPath().isEmpty() || uri.getPath().equals("/")
                ? uri.getHost()
                : uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
    }

}
