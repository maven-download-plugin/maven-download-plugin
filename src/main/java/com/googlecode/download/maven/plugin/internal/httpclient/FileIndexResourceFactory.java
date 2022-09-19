package com.googlecode.download.maven.plugin.internal.httpclient;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.cache.InputLimit;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.cache.ResourceFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Generates {@link Resource} instances whose body is stored in a temporary file.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class FileIndexResourceFactory implements ResourceFactory {

    private static final Pattern DOMAIN_REGEX = Pattern.compile("^(?:[^/]*//)?([^/]+)");
    private final Path cacheDir;

    public FileIndexResourceFactory(final Path cacheDir) {
        super();
        this.cacheDir = cacheDir;
    }

    protected Path generateUniqueCacheFile(final String uri) {
        Matcher domainMatcher = DOMAIN_REGEX.matcher(uri);
        String domainPrefix = domainMatcher.find() ? domainMatcher.group(0) + '_' + DigestUtils.md5Hex(uri) : "";
        return this.cacheDir.resolve(domainPrefix + DigestUtils.md5Hex(uri));
    }

    @Override
    public Resource generate(
            final String requestId,
            final InputStream inStream,
            final InputLimit limit) throws IOException {
        final Path cachedFile = generateUniqueCacheFile(requestId);
        Files.copy(inStream, cachedFile);
        return new FileIndexResource(cachedFile);
    }

    @Override
    public Resource copy(
            final String requestId,
            final Resource resource) throws IOException {
        final Path dst = generateUniqueCacheFile(requestId);

        if (resource instanceof FileIndexResource) {
            Files.copy(((FileIndexResource) resource).getPath(), dst, REPLACE_EXISTING);
        } else {
            try (InputStream is = resource.getInputStream())
            {
                Files.copy(is, dst, REPLACE_EXISTING);
            }
        }
        return new FileIndexResource(dst);
    }

}
