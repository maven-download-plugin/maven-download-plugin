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
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.maven.plugin.logging.Log;

/**
 * {@link ProgressReport} implementation that logs operation progress at INFO priority.
 */
@NotThreadSafe
public final class LoggingProgressReport implements ProgressReport {

    /**
     * Represents the number of bytes in a kilobyte, used for unit conversion.
     */
    private static final long KBYTE = 1024L;

    /**
     * Character representing the unit for kilobytes.
     */
    private static final char K_UNIT = 'K';

    /**
     * Character representing the unit for bytes.
     */
    private static final char B_UNIT = 'b';

    /**
     * Logger.
     */
    private final Log log;

    /**
     * Character representing the unit of measurement used to display download progress.
     */
    private char unit;

    /**
     * Represents the total length of the resource content being downloaded (in bytes).
     */
    private long total;

    /**
     * Represents the number of bytes that have been successfully downloaded.
     */
    private long completed;

    /**
     * Constructor.
     * @param logger Logger.
     */
    public LoggingProgressReport(final Log logger) {
        this.log = logger;
    }

    @Override
    public void initiate(final URI uri, final long totalBytes) {
        this.total = totalBytes;
        this.completed = 0L;
        this.unit = totalBytes >= KBYTE ? K_UNIT : B_UNIT;
        this.log.info(String.format("%s: %s", "Downloading", uri));
    }

    @Override
    public void update(final long bytesRead) {
        this.completed += bytesRead;
        final String totalInUnits;
        final long completedInUnits;
        if (this.unit == K_UNIT) {
            totalInUnits = this.total == -1L
                ? "?" : Long.toString(this.total / KBYTE) + this.unit;
            completedInUnits = this.completed / KBYTE;
        } else {
            totalInUnits = this.total == -1L ? "?" : Long.toString(this.total);
            completedInUnits = this.completed;
        }
        this.log.info(String.format("%d/%s", completedInUnits, totalInUnits));
    }

    @Override
    public void completed() {
        this.log.info(
            String.format(
                "%s downloaded",
                LoggingProgressReport.render(this.completed, this.unit)
            )
        );
    }

    @Override
    public void error(final Exception exc) {
        this.log.error(exc);
    }

    /**
     * Converts a byte count into a string representation, optionally converting to kilobytes
     * based on the specified unit.
     *
     * @param bytes The number of bytes.
     * @param unit The unit of measurement ('K' for kilobytes, 'b' for bytes).
     * @return The string representation of the byte count, optionally converted to kilobytes.
     */
    private static String render(final long bytes, final char unit) {
        return unit == K_UNIT ? Long.toString(bytes / KBYTE) + unit : Long.toString(bytes);
    }
}
