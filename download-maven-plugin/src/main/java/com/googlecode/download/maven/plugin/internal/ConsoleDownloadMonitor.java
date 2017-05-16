package com.googlecode.download.maven.plugin.internal;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.FileUtils;

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
        transferInitiated(event.getResource().getName(), event.getWagon().getRepository().getUrl(), event.getRequestType());
    }

    /**
     * Display the transfer started message
     * @param downloadUrl full download url to file
     * @param requestType One of values returnable by {@link TransferEvent#getRequestType()}
     */
	public void transferInitiated(final String resourceName, final String downloadUrl, final int requestType) {
        this.completed = 0L;
        this.log.info(
            String.format(
                "%s: %s/%s",
                requestType == TransferEvent.REQUEST_PUT ? "Uploading" : "Downloading",
                downloadUrl,
                resourceName
            )
        );
	}

    @Override
    public void transferStarted(final TransferEvent event) {
        // This space left intentionally blank
    }


	@Override
	public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
		final long total = transferEvent.getResource().getContentLength();
		this.completed += (long) length;
		transferProgress(completed, total);
	}
    /**
     * Output transfer progress message in a pluggable way
     * @param total
     */
	public void transferProgress(final long alreadyDownloaded, final long total) {
		log.info(getTransferMessage(alreadyDownloaded, total));
	}

	/**
	 * Create a transfer message from total read size
	 * @param alreadyDownloaded
	 * @param total
	 * @return
	 */
	private String getTransferMessage(final long alreadyDownloaded, final long total) {
		final String message = null;
		return FileUtils.byteCountToDisplaySize(alreadyDownloaded) + "/" +
						( total == WagonConstants.UNKNOWN_LENGTH ? "?" : FileUtils.byteCountToDisplaySize(total) ) +
			    "\r";
	}

    @Override
	public void transferCompleted( final TransferEvent transferEvent )
    {
        final long contentLength = transferEvent.getResource().getContentLength();
        final int requestType = transferEvent.getRequestType();
        transferCompleted(contentLength, requestType);
    }

    /**
     * Output transfer terminated message
     * @param contentLength total content length
     * @param requestType One of values returnable by {@link TransferEvent#getRequestType()}
     */
	public void transferCompleted(final long length, final int requestType) {
		if ( length != WagonConstants.UNKNOWN_LENGTH )
        {
            this.log.info(
                    String.format(
                        "%s %s",
                        requestType == TransferEvent.REQUEST_PUT ? "uploaded" : "downloaded",
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

    /**
     * Decorate an output stream by writing regularly the download progress.
     * Notice publishing to outputstream is done once each second, by using a simple time check.
     * Maybe poor, but allow me that laziness for the sake of not one more thread.
     * @param openOutputStream
     * @return
     */
	public OutputStream decorate(final OutputStream openOutputStream, final long totalSize) {
		return new OutputStream() {

			/**
			 * Number of written bytes
			 */
			private int written = 0;

			/**
			 * Time last message was written at
			 */
			private long lastMessageWasWrittenAt;

			/**
			 * Write something if one second (at least) has elapsed since last message
			 */
			private void maybeWrite() {

				final long timeInSeconds = System.currentTimeMillis()/1000;
				if(timeInSeconds-lastMessageWasWrittenAt>0) {
					transferProgress(written, totalSize);
					lastMessageWasWrittenAt = timeInSeconds;
				}
			}

			@Override
			public void write(final int b) throws IOException {
				openOutputStream.write(b);
				written++;
				maybeWrite();
			}

			@Override
			public void write(final byte[] abyte0) throws IOException {
				openOutputStream.write(abyte0);
				written += abyte0.length;
				maybeWrite();
			}

			@Override
			public void write(final byte[] abyte0, final int offset, final int length) throws IOException {
				openOutputStream.write(abyte0, offset, length);
				written += length;
				maybeWrite();
			}

			@Override
			public void flush() throws IOException {
				openOutputStream.flush();
			}

			@Override
			public void close() throws IOException {
				openOutputStream.close();
			}
		};
	}
}
