package com.googlecode.download.maven.plugin.internal.cache;

/**
 * Thrown when {@link DownloadCache} fails to read an existing index.
 * <p>
 * This occurs when upgrading to a new version of the plugin with breaking changes in the index storage strategy
 * (including Java serialization changes, or even moving to a different serialization mechanism (JSON, XML, etc.).
 */
class IncompatibleIndexException extends RuntimeException {
    IncompatibleIndexException(Exception cause) {
        super(cause);
    }
}
