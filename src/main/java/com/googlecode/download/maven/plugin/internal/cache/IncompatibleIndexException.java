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
package com.googlecode.download.maven.plugin.internal.cache;

/**
 * Thrown when {@link FileBackedIndex} fails to read an existing index.
 * <p>This occurs when upgrading to a new version of the plugin with breaking changes
 * in the index storage strategy (including Java serialization changes, or even moving
 * to a different serialization mechanism (JSON, XML, etc.).</p>
 */
final class IncompatibleIndexException extends Exception {
    /**
     * Constructs a new IncompatibleIndexException with the specified cause.
     * @param cause The underlying exception that caused this exception to be thrown.
     */
    IncompatibleIndexException(final Exception cause) {
        super(cause);
    }
}
