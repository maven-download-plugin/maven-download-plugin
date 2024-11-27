/*
 * Copyright 2009-2018 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.download.maven.plugin.internal;

import java.net.URI;

/**
 * Common utilities used by the {@link WGetMojo}.
 */
public final class FileNameUtils {

    /**
     * Private constructor.
     */
    private FileNameUtils() {
    }

    /**
     * Attempts to construct the target file name based on a URI as the relative resource name
     * or, if the root resource is requested, the host name extracted from the URI.
     * @param uri URI to extract the output name from.
     * @return Output file name based on the URI.
     */
    public static String getOutputFileName(final URI uri) {
        final String result;
        if (uri.getPath().isEmpty() || "/".equals(uri.getPath())) {
            result = uri.getHost();
        } else {
            result = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
        }
        return result;
    }

}
