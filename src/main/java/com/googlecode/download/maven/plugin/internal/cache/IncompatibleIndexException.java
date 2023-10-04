package com.googlecode.download.maven.plugin.internal.cache;

/**
 * <p>Thrown when {@link FileBackedIndex} fails to read an existing index.</p>
 * <p>This occurs when upgrading to a new version of the plugin with breaking changes in the index storage strategy
 * (including Java serialization changes, or even moving to a different serialization mechanism (JSON, XML, etc.).</p>
 */
class IncompatibleIndexException extends Exception {
    IncompatibleIndexException(Exception cause) {
        super(cause);
    }
}
