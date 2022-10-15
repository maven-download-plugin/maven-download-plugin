package com.googlecode.download.maven.plugin.internal.cache;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class FileIndexResource implements Resource {
    private final Path path;

    private final Path cacheDir;

    public FileIndexResource(final Path path, Path cacheDir) {
        this.path = path;
        this.cacheDir = cacheDir;
    }

    public Path getPath() {
        return path;
    }

    public Path getFullPath() {
        return cacheDir.resolve(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(getFullPath());
    }

    @Override
    public long length() {
        try {
            return Files.size(getFullPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        // do nothing
    }
}
