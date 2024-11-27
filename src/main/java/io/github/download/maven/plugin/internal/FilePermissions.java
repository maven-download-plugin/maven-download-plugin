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

import java.io.File;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/**
 * File permissions change helper.
 */
public final class FilePermissions {

    /**
     * String code for set executable permission.
     */
    private static final String SET_EXECUTABLE = "+x";

    /**
     * Permission change operation(s) to apply to a file.
     */
    private final String operations;

    /**
     * Logger.
     */
    private final Log log;

    /**
     * Constructor.
     * @param operations Permission change operation(s) to apply to a file.
     * @param log Logger.
     */
    public FilePermissions(final String operations, final Log log) {
        this.operations = operations;
        this.log = log;
    }

    /**
     * Apply permission operation(s) to the file.
     * @param file File to apply permission change operations.
     * @throws MojoExecutionException If failed to apply the operation.
     */
    public void applyTo(final File file) throws MojoExecutionException {
        if (this.operations != null) {
            if (this.operations.contains(SET_EXECUTABLE)) {
                this.setExecutable(file);
            } else {
                throw new MojoExecutionException("Invalid outputFilePermissions: " + this.operations);
            }
        }
    }

    /**
     * Grant executable permissions to the file.
     * @param file File to apply permission change.
     * @throws MojoExecutionException If failed to apply the operation.
     */
    private void setExecutable(final File file) throws MojoExecutionException {
        if (file.setExecutable(true)) {
            this.log.debug(
                String.format("Applied (%s) permission to file %s", this.operations, file.getAbsolutePath())
            );
        } else {
            throw new MojoExecutionException(
                String.format("Failed to apply (%s) to file %s", this.operations, file.getAbsolutePath())
            );
        }
    }
}
