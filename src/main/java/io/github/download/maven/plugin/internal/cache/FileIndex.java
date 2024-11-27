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
package io.github.download.maven.plugin.internal.cache;

import java.net.URI;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Convenient map to search for the path where file is locally stored
 * by uri of the resource the file was downloaded from.
 * Implementations should not read/write file bodies using stored paths.
 *
 * @author Paul Polishchuk
 * @since 1.3.1
 */
interface FileIndex {

    /**
     * Adds given path to the index using uri parameter as a key.
     * @param uri Index key.
     * @param path Index value.
     */
    void put(URI uri, String path);

    /**
     * Gets stored value by the key.
     * @param uri Index key
     * @return Path by given uri key; {@literal null} if not found.
     */
    String get(URI uri);

    /**
     * The lock to be used when accessing the index.
     * @return The lock.
     */
    ReentrantLock getLock();
}
