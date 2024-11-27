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
package io.github.download.maven.plugin.internal.checksum;

/**
 * Supported checksum types with corresponding digest algos.
 * @author Paul Polishchuk
 */
enum Checksum {

    /**
     * MD5 checksum type.
     */
    MD5("MD5"),

    /**
     * SHA1 checksum type.
     */
    SHA1("SHA1"),

    /**
     * SHA-256 checksum type.
     */
    SHA256("SHA-256"),

    /**
     * SHA-512 checksum type.
     */
    SHA512("SHA-512");

    /**
     * Algorithm string constant.
     */
    private final String algorithm;

    /**
     * Constructor.
     * @param algo Algorithm string constant.
     */
    Checksum(final String algo) {
        this.algorithm = algo;
    }

    /**
     * Get algorithm constant string.
     * @return Algorithm constant string.
     */
    String algo() {
        return this.algorithm;
    }
}
