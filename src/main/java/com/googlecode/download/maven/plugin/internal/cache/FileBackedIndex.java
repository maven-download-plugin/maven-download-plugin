package com.googlecode.download.maven.plugin.internal.cache;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.Args;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.concurrent.ThreadSafe;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpVersion.HTTP_1_1;

/**
 * Persistent cache implementation of the {@link HttpCacheStorage} interface,
 * to be used with the Apache HttpClient Cache, using a directory with
 * the copy of the files and a serialized file map.
 *
 * @author Paul Polishchuk
 * @since 1.3.1
 */
@ThreadSafe
public final class FileBackedIndex implements HttpCacheStorage {

    private static final Pattern URI_REGEX = Pattern.compile("^(?:\\{.*})?([^/]+//?.*)$");
    private static final String CACHE_FILENAME = "index.ser";
    private final static StatusLine OK_STATUS_LINE = new BasicStatusLine(HTTP_1_1, SC_OK, "OK");
    private final Map<URI, String> index = new ConcurrentHashMap<>();
    private final Path cacheIndexFile;
    private final Log log;
    private final Path baseDir;

    private static HttpCacheEntry asHttpCacheEntry( Path path, Path cacheDir) {
        Date lastModifiedDate;
        try {
            lastModifiedDate = Date.from(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant());
        } catch (IOException e) {
            lastModifiedDate = Date.from(Instant.now());
        }
        return new HttpCacheEntry(lastModifiedDate, Date.from(Instant.now()), OK_STATUS_LINE,
                new Header[] { new BasicHeader(HttpHeaders.DATE, DateUtils.formatDate(lastModifiedDate)),
                new BasicHeader(HeaderConstants.CACHE_CONTROL_MAX_AGE, String.valueOf(Integer.MAX_VALUE)),
                new BasicHeader(HeaderConstants.EXPIRES,
                        DateUtils.formatDate(Date.from(Instant.now().plus(365, DAYS))))},
                new FileIndexResource(path, cacheDir));
    }

    private static Path asPath( HttpCacheEntry entry) {
        return ((FileIndexResource) entry.getResource()).getPath();
    }

    /**
     * Creates index backed by file "index.ser" in baseDir.
     * Throws runtime exceptions if baseDir does not exist, not a directory
     * or file can't be created in it.
     * @param baseDir directory where the index file should be stored.
     */
    public FileBackedIndex(final Path baseDir, Log log) throws NotDirectoryException {
        this.log = log;
        this.baseDir = baseDir;
        this.cacheIndexFile = Paths.get(baseDir.toString(), CACHE_FILENAME);
    }

    protected static URI asUri(String key) {
        Matcher uriMatcher = URI_REGEX.matcher(key);
        if (uriMatcher.find()) {
            try {
                return normalize(URI.create(uriMatcher.group(1)));
            } catch (URISyntaxException e) {
                return null;
            }
        }
        return null;
    }

    private static URI normalize(final URI requestUri) throws URISyntaxException {
        Args.notNull(requestUri, "URI");
        final URIBuilder builder = new URIBuilder(requestUri) ;
        if (builder.getHost() != null) {
            if (builder.getScheme() == null) {
                builder.setScheme("http");
            }
            if (builder.getPort() > -1) {
                builder.setPort(-1);
            }
        }
        builder.setFragment(null);
        return builder.build();
    }

    @Override
    public void putEntry(String key, HttpCacheEntry entry) throws IOException {
        URI uri = asUri(key);
        if (uri != null) {
            log.debug("Putting \"" + uri + "\" into cache");
            this.index.put(uri, asPath(entry).toString());
            try {
                this.load(cacheIndexFile);
            } catch (IncompatibleIndexException e) {
                log.warn("Could not load index cache index file, it will be rewritten.");
            }
            this.save();
        } else {
            log.warn("Could not extract an URI from key: " + key);
        }
    }

    @Override
    public HttpCacheEntry getEntry(String uriString) {
        try {
            this.load(cacheIndexFile);
        }
        catch (IncompatibleIndexException | IOException e) {
            log.warn("Error while reading from cache " + cacheIndexFile);
        }
        URI uri = asUri(uriString);
        if (!this.index.containsKey(uri)) {
            log.debug("Current cache: " + this.index.keySet().stream()
                            .map(u ->"\"" + u + "\"")
                    .collect(Collectors.joining(", ")) + " does not contain \"" + uri + "\"");
            return null;
        }
        Path cachedFile = Paths.get(this.index.get(uri));
        if (!Files.exists(baseDir.resolve(cachedFile))) {
            log.debug("Cached version of " + uri + " is gone; deleting cache entry");
            this.index.remove(uri);
            try {
                this.load(cacheIndexFile);
                this.save();
            } catch (IncompatibleIndexException e) {
                log.warn("Could not load index cache index file, it will be rewritten.");
            } catch (IOException e) {
                log.warn("Unable to update cache.");
            }
        }
        return asHttpCacheEntry(cachedFile, baseDir);
    }

    /**
     * Loads index from the file storage replacing absent in-memory entries.
     * @param store file where index is persisted.
     * @throws IncompatibleIndexException is the store cannot be read due to a deserialization issue
     */
    @SuppressWarnings("unchecked")
    private void load(final Path store) throws IncompatibleIndexException, IOException {
        if (Files.exists(store) && Files.size(store) != 0L) {
            try (
                    final RandomAccessFile file = new RandomAccessFile(store.toFile(), "r");
                    final FileChannel channel = file.getChannel();
                    final FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true);
                    final ObjectInputStream deserialize = new ObjectInputStream(Files.newInputStream(store))
            ) {
                Map<URI, String> newEntries = (Map<URI, String>) deserialize.readObject();
                newEntries.forEach(index::putIfAbsent);
            } catch (final ClassNotFoundException | InvalidClassException e) {
                throw new IncompatibleIndexException(e);
            }
        }
    }

    /**
     * Saves current im-memory index to file based storage.
     */
    private void save() {
        try (
                final FileOutputStream file = new FileOutputStream(this.cacheIndexFile.toFile());
                final ObjectOutput res = new ObjectOutputStream(file);
                final FileChannel channel = file.getChannel();
                final FileLock ignored = channel.lock()
        ) {
            res.writeObject(new HashMap<URI, String>(this.index));
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void removeEntry(String key) {
        assert false; // This method should not have been called
    }

    @Override
    public void updateEntry(String key, HttpCacheUpdateCallback callback) {
        assert false; // This method should not have been called
    }
}
