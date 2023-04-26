package com.googlecode.download.maven.plugin.internal;

/**
 * Represents a download failure exception, thrown when the requested resource returns
 * a non-20x HTTP code.
 */
public final class DownloadFailureException extends RuntimeException {

    private final int statusCode;

    private final String statusLine;

    /**
     * @return the HTTP status code.
     */
    public int getHttpCode() {
        return statusCode;
    }

    /**
     * @return the HTTP status line.
     */
    public String getStatusLine() {
        return statusLine;
    }

    /**
     * Creates a new instance.
     * @param statusCode HTTP code
     * @param statusLine status line
     */
    public DownloadFailureException(int statusCode, String statusLine) {
        this.statusCode = statusCode;
        this.statusLine = statusLine;
    }

    @Override
    public String getMessage() {
        return "Download failed with code " + getHttpCode() + ": " + getStatusLine();
    }
}
