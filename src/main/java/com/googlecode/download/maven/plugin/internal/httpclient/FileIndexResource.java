package com.googlecode.download.maven.plugin.internal.httpclient;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class FileIndexResource implements Resource {
    private final Path path;

    public FileIndexResource(final Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public long length() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        // do nothing
    }
}
