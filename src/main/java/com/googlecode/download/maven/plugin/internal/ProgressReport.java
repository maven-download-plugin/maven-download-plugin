/**
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

import java.net.URI;

/**
 * Progress report of a file download operation.
 */
public interface ProgressReport {

    /**
     * Triggered to signal initiation of a download operation.
     *
     * @param uri the URI of the resource being downloaded
     * @param total the total length of resource content.
     */
    void initiate(URI uri, long total);

    /**
     * Triggered to signal successful retrieval of a chunk
     * of the resource content.
     *
     * @param bytesRead the number of bytes retrieved.
     */
    void update(long bytesRead);

    /**
     * Triggered to signal completion of the download operation.
     */
    void completed();

    /**
     * Triggered to signal an error occurred during the download operation.
     */
    void error(Exception ex);

}
