package com.googlecode.download.maven.plugin.internal.cache;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.InputLimit;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.cache.ResourceFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Generates {@link Resource} instances whose body is stored in a temporary file.
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class FileIndexResourceFactory implements ResourceFactory {
    private final Path cacheDir;

    public FileIndexResourceFactory(final Path cacheDir) {
        super();
        this.cacheDir = cacheDir;
    }

    protected Path generateUniqueCachePath(final String uri) {
        String resourcePart = !uri.isEmpty()
                ? uri.substring(uri.lastIndexOf('/') + 1)
                : uri;
        resourcePart = resourcePart.isEmpty()
                ? resourcePart
                : resourcePart + "_";
        // append a unique string based on timestamp
        return Paths.get(resourcePart +
                DigestUtils.md5Hex(UUID.randomUUID().toString()));
    }

    @Override
    public Resource generate(
            final String requestId,
            final InputStream inStream,
            final InputLimit limit) throws IOException {
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        final Path cachedFile = generateUniqueCachePath(requestId);
        Files.copy(inStream, cacheDir.resolve(cachedFile), REPLACE_EXISTING);
        return new FileIndexResource(cachedFile, cacheDir);
    }

    @Override
    public Resource copy(
            final String requestId,
            final Resource resource) throws IOException {
        final Path dst = generateUniqueCachePath(requestId);

        if (resource instanceof FileIndexResource) {
            Files.copy(cacheDir.resolve(((FileIndexResource) resource).getPath()), cacheDir.resolve(dst),
                    REPLACE_EXISTING);
        } else {
            try (InputStream is = resource.getInputStream())
            {
                Files.copy(is, cacheDir.resolve(dst), REPLACE_EXISTING);
            }
        }
        return new FileIndexResource(dst, cacheDir);
    }

}
