package com.googlecode.download.maven.plugin.internal.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Binary file backed index.
 * The implementation is <b>NOT</b> thread safe and should be synchronized
 * by the lock.
 * @author Paul Polishchuk
 * @since 1.3.1
 */
@NotThreadSafe
final class FileBackedIndex implements FileIndex {

    private final Map<URI, String> index = new HashMap<>();
    private final File storage;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates index backed by file "index.ser" in baseDir.
     * Throws runtime exceptions if baseDir does not exist, not a directory
     * or file can't be created in it.
     * @param baseDir directory where the index file should be stored.
     */
    FileBackedIndex(final File baseDir) {
        if (!baseDir.isDirectory()) {
            throw new IllegalArgumentException(
                String.format(
                    "Cannot use %s as cache directory: not exist or not a directory",
                    baseDir.getAbsolutePath()
                )
            );
        }
        final File store = new File(baseDir, "index.ser");
        if (store.exists()) {
            try {
                this.loadFrom(store);
            } catch (final IncompatibleIndexException e) {
                FileBackedIndex.deleteIncompatible(store);
                FileBackedIndex.create(store);
            }
        } else {
            FileBackedIndex.create(store);
        }
        this.storage = store;
    }

    @Override
    public void put(final URI uri, final String path) {
        this.index.put(uri, path);
        this.save();
    }

    @Override
    public String get(final URI uri) {
        this.loadFrom(this.storage);
        return this.index.get(uri);
    }

    @Override
    public ReentrantLock getLock() {
        return this.lock;
    }
    /**
     * Create storage file.
     * @param store File to be created.
     */
    private static void create(final File store) {
        try {
            if (!store.createNewFile()) {
                throw new IllegalStateException(
                    String.format(
                        "Failed to create index storage file %s",
                        store.getAbsolutePath()
                    )
                );
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void deleteIncompatible(final File store) {
        if (!store.delete()) {
            throw new IllegalStateException(
                String.format(
                    "Failed to delete incompatible index storage file %s",
                    store.getAbsolutePath()
                )
            );
        }
    }

    /**
     * Loads index from the file storage replacing all in-memory entries.
     * @param store file where index is persisted.
     * @throws IncompatibleIndexException is the store cannot be read due to a deserialization issue
     */
    @SuppressWarnings("unchecked")
    private void loadFrom(final File store) throws IncompatibleIndexException {
        if (store.length() != 0L) {
            try (
                final RandomAccessFile file = new RandomAccessFile(store, "r");
                final FileChannel channel = file.getChannel();
                final FileLock lock = channel.lock(0L, Long.MAX_VALUE, true);
                final ObjectInputStream deserialize = new ObjectInputStream(new FileInputStream(store))
            ) {
                this.index.clear();
                this.index.putAll((Map<URI, String>) deserialize.readObject());
            } catch (final ClassNotFoundException | InvalidClassException e) {
                throw new IncompatibleIndexException(e);
            } catch (final IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    /**
     * Saves current im-memory index to file based storage.
     */
    private void save() {
        try (
            final FileOutputStream file = new FileOutputStream(this.storage);
            final ObjectOutput res = new ObjectOutputStream(file);
            final FileChannel channel = file.getChannel();
            final FileLock lock = channel.lock()
        ) {
            res.writeObject(this.index);
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
