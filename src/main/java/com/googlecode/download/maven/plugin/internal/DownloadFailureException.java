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
package com.googlecode.download.maven.plugin.internal;

/**
 * Represents a download failure exception, thrown when the requested resource returns
 * a non-20x HTTP code.
 */
public final class DownloadFailureException extends RuntimeException {

    /**
     * HTTP status code.
     */
    private final int statusCode;

    /**
     * HTTP status line.
     */
    private final String statusLine;

    /**
     * Creates a new instance.
     * @param statusCode HTTP code
     * @param statusLine Status line
     */
    public DownloadFailureException(final int statusCode, final String statusLine) {
        super();
        this.statusCode = statusCode;
        this.statusLine = statusLine;
    }

    /**
     * Get HTTP status code.
     * @return HTTP status code.
     */
    public int getHttpCode() {
        return this.statusCode;
    }

    @Override
    public String getMessage() {
        return String.format("Download failed with code %d: %s", this.statusCode, this.statusLine);
    }
}
