package com.googlecode;

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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.wagon.WagonConstants;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.codehaus.plexus.logging.AbstractLogEnabled;

/**
 * Console download progress meter.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id: ConsoleDownloadMonitor.java 191492 2005-06-20 15:21:50Z brett $
 */
public class ConsoleDownloadMonitor
    extends AbstractLogEnabled
    implements TransferListener
{
    private long complete;

    public void transferInitiated( TransferEvent transferEvent )
    {
        String url = transferEvent.getWagon().getRepository().getUrl();
		int requestType = transferEvent.getRequestType();

        // TODO: can't use getLogger() because this isn't currently instantiated as a component
        String downloadUrl = url + "/" + transferEvent.getResource().getName();
        
        outputTransferStarted(downloadUrl, requestType);

        complete = 0;
    }

    /**
     * Display the transfer started message
     * @param downloadUrl full download url to file
     * @param requestType One of values returnable by {@link TransferEvent#getRequestType()}
     */
	public void outputTransferStarted(String downloadUrl, int requestType) {
		String message = requestType == TransferEvent.REQUEST_PUT ? "Uploading" : "Downloading";
		message = message + ": " + downloadUrl;
        System.out.println( message );
	}

    public void transferStarted( TransferEvent transferEvent )
    {
        // This space left intentionally blank
    }

    public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
    {
        long total = transferEvent.getResource().getContentLength();
        complete += length;
        // TODO [BP]: Sys.out may no longer be appropriate, but will \r work with getLogger()?
        outputTransferProgress(complete, total);
    }

    /**
     * Output transfer progress message in a pluggable way
     * @param total
     */
	public void outputTransferProgress(long alreadyDownloaded, long total) {
		System.out.print(getTransferMessage(alreadyDownloaded, total) );
	}

	/**
	 * Create a transfer message from total read size
	 * @param alreadyDownloaded
	 * @param total
	 * @return
	 */
	private String getTransferMessage(long alreadyDownloaded, long total) {
		String message = null;
		return FileUtils.byteCountToDisplaySize(alreadyDownloaded) + "/" + 
						( total == WagonConstants.UNKNOWN_LENGTH ? "?" : FileUtils.byteCountToDisplaySize(total) ) +
			    "\r";
	}

    public void transferCompleted( TransferEvent transferEvent )
    {
        long contentLength = transferEvent.getResource().getContentLength();
        int requestType = transferEvent.getRequestType();
        outputTransferTerminated(contentLength, requestType);
    }

    /**
     * Output transfer terminated message
     * @param contentLength total content length
     * @param requestType One of values returnable by {@link TransferEvent#getRequestType()}
     */
	public void outputTransferTerminated(long contentLength, int requestType) {
		if ( contentLength != WagonConstants.UNKNOWN_LENGTH )
        {
			String type = ( requestType == TransferEvent.REQUEST_PUT ? "uploaded" : "downloaded" );
            System.out.println( FileUtils.byteCountToDisplaySize(contentLength) + " " + type );
        }
	}

    public void transferError( TransferEvent transferEvent )
    {
        // TODO: can't use getLogger() because this isn't currently instantiated as a component
        transferEvent.getException().printStackTrace();
    }

    public void debug( String message )
    {
        // TODO: can't use getLogger() because this isn't currently instantiated as a component
//        getLogger().debug( message );
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
				
				long timeInSeconds = System.currentTimeMillis()/1000;
				if(timeInSeconds-lastMessageWasWrittenAt>0) {
					outputTransferProgress(written, totalSize);
					lastMessageWasWrittenAt = timeInSeconds;
				}
			}
			
			@Override
			public void write(int b) throws IOException {
				openOutputStream.write(b);
				written++;
				maybeWrite();
			}

			@Override
			public void write(byte[] abyte0) throws IOException {
				openOutputStream.write(abyte0);
				written += abyte0.length;
				maybeWrite();
			}
			
			@Override
			public void write(byte[] abyte0, int offset, int length) throws IOException {
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



