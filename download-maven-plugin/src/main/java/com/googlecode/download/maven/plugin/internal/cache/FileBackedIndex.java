package com.googlecode.download.maven.plugin.internal.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;

/**
 * Binary file backed index.
 * @author Paul Polishchuk
 * @since 1.3.1
 */
final class FileBackedIndex implements FileIndex {

    private final Map<URL, String> index = new HashMap<>();
    private final File storage;

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
            this.loadFrom(store);
        } else {
            FileBackedIndex.create(store);
        }
        this.storage = store;
    }

    @Override
    public void put(final URL url, final String path) {
        this.index.put(url, path);
        this.save();
    }

    @Override
    public boolean contains(final URL url) {
        this.loadFrom(this.storage);
        return this.index.containsKey(url);
    }

    @Override
    public String get(final URL url) {
        if (this.contains(url)) {
            return this.index.get(url);
        }
        throw new IllegalStateException(
            "Cache miss. Check for existence with FileIndex#contains before"
        );
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

    /**
     * Loads index from the file storage replacing all in-memory entries.
     * @param store file where index is persisted.
     */
    @SuppressWarnings("unchecked")
    private void loadFrom(final File store) {
        if (store.length() != 0L) {
            try (
                final RandomAccessFile file = new RandomAccessFile(store, "r");
                final FileChannel channel = file.getChannel();
                final FileLock lock = channel.lock(0L, Long.MAX_VALUE, true);
                final ObjectInputStream deserialize = new ObjectInputStream(new FileInputStream(store))
            ) {
                this.index.clear();
                this.index.putAll((Map<URL, String>) deserialize.readObject());
            } catch (final IOException | ClassNotFoundException ex) {
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
