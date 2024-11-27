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

/**
 * Http response codes.
 */
@SuppressWarnings("checkstyle:JavadocVariable")
public enum HttpCodes {
    MOVED_PERMANENTLY(301),
    SEE_OTHER(303),
    BAD_REQUEST(400),
    INTERNAL_SERVER_ERROR(500);

    private final int code;

    /**
     * Constructor.
     * @param httpCode Numeric code.
     */
    HttpCodes(final int httpCode) {
        this.code = httpCode;
    }

    /**
     * Numeric code.
     * @return Numeric code.
     */
    public int getCode() {
        return this.code;
    }
}
