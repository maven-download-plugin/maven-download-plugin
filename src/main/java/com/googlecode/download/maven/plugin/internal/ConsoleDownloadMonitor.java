package com.googlecode.download.maven.plugin.internal;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.wagon.WagonConstants;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;

/**
 * Console download progress meter.
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id: ConsoleDownloadMonitor.java 191492 2005-06-20 15:21:50Z brett $
 */
final class ConsoleDownloadMonitor implements TransferListener {

    private static final String PROGRESS_FORMAT = "%d/%s";
    private static final long KBYTE = 1024L;

    private final Log log;
    private long completed;

    ConsoleDownloadMonitor(final Log logger) {
        this.log = logger;
        this.completed = 0L;
    }

    @Override
    public void transferInitiated(final TransferEvent event) {
        this.completed = 0L;
        this.log.info(
            String.format(
                "%s: %s/%s",
                event.getRequestType() == TransferEvent.REQUEST_PUT ? "Uploading" : "Downloading",
                event.getWagon().getRepository().getUrl(),
                event.getResource().getName()
            )
        );
    }

    @Override
    public void transferStarted(final TransferEvent event) {
        // This space left intentionally blank
    }

    @Override
    public void transferProgress(final TransferEvent event, final byte[] buffer, final int length) {
        final long total = event.getResource().getContentLength();
        this.completed += (long) length;
        final String totalInUnits;
        final long completedInUnits;
        if (total >= KBYTE) {
            totalInUnits = total == WagonConstants.UNKNOWN_LENGTH ? "?" : (total / KBYTE) + "K";
            completedInUnits = this.completed / KBYTE;
        } else {
            totalInUnits = total == WagonConstants.UNKNOWN_LENGTH ? "?" : total + "b";
            completedInUnits = this.completed;
        }
        this.log.info(String.format(PROGRESS_FORMAT, completedInUnits, totalInUnits));
    }

    @Override
    public void transferCompleted(final TransferEvent event) {
        final long length = event.getResource().getContentLength();
        if (length != (long) WagonConstants.UNKNOWN_LENGTH) {
            this.log.info(
                String.format(
                    "%s %s",
                    event.getRequestType() == TransferEvent.REQUEST_PUT ? "uploaded" : "downloaded",
                    length >= KBYTE ? (length / KBYTE) + "K" : length + "b"
                )
            );
        }
    }

    @Override
    public void transferError(final TransferEvent event) {
        this.log.error(event.getException());
    }

    @Override
    public void debug(final String message) {
        this.log.debug(message);
    }
}
