/*
 * Copyright 2009-2018 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.download.maven.plugin.internal.checksum;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

/**
 * Checksums supplied to verify file integrity.
 * @author Paul Polishchuk
 */
public final class Checksums {

    /**
     * Number of bytes in kByte.
     */
    private static final int KBYTE = 1024;

    /**
     * A map of a checksum type to a digest.
     */
    private final Map<Checksum, String> supplied;

    /**
     * Constructor.
     * @param md5 Supplied md5 checksum, may be {@literal null}.
     * @param sha1 Supplied sha1 checksum, may be {@literal null}.
     * @param sha256 Supplied sha256 checksum, may be {@literal null}.
     * @param sha512 Supplied sha512 checksum, may be {@literal null}.
     * @param log Logger.
     */
    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:ParameterName"})
    public Checksums(
        @Nullable final String md5, @Nullable final String sha1,
        @Nullable final String sha256, @Nullable final String sha512,
        final Log log
    ) {
        this.supplied = Checksums.create(md5, sha1, sha256, sha512);
        if (this.supplied.isEmpty()) {
            log.debug("No checksums were supplied, skipping file validation");
        } else if (this.supplied.size() > 1) {
            log.warn("More than one checksum is supplied. This may be slow for big files. Consider using a single checksum.");
        }
    }

    /**
     * Validates the file with supplied checksums.
     * @param file File to validate.
     * @return True if the file matches all supplied checksums
     *  or if no checksums were supplied.
     */
    @SuppressWarnings("checkstyle.IllegalCatch")
    public boolean isValid(final File file) {
        boolean valid = true;
        try {
            this.validate(file);
        } catch (final Exception ex) {
            valid = false;
        }
        return valid;
    }

    /**
     * Validates the file with supplied checksums.
     * @param file File to validate.
     * @throws Exception If the file didn't match any supplied checksum.
     */
    public void validate(final File file) throws Exception {
        for (final Map.Entry<Checksum, String> entry : this.supplied.entrySet()) {
            Checksums.verifyChecksum(
                file, entry.getValue(),
                MessageDigest.getInstance(entry.getKey().algo())
            );
        }
    }

    /**
     * Fill the map with checksums.
     * @param md5 Supplied md5 checksum, may be {@literal null}.
     * @param sha1 Supplied sha1 checksum, may be {@literal null}.
     * @param sha256 Supplied sha256 checksum, may be {@literal null}.
     * @param sha512 Supplied sha512 checksum, may be {@literal null}.
     * @return A map of a checksum type to a digest; {@literal null} digests
     *  are not included.
     */
    @SuppressWarnings("checkstyle:ParameterName")
    private static Map<Checksum, String> create(
        @Nullable final String md5, @Nullable final String sha1,
        @Nullable final String sha256, @Nullable final String sha512
    ) {
        final Map<Checksum, String> digests = new EnumMap<>(Checksum.class);
        if (md5 != null) {
            digests.put(Checksum.MD5, md5);
        }
        if (sha1 != null) {
            digests.put(Checksum.SHA1, sha1);
        }
        if (sha256 != null) {
            digests.put(Checksum.SHA256, sha256);
        }
        if (sha512 != null) {
            digests.put(Checksum.SHA512, sha512);
        }
        return digests;
    }

    /**
     * Verifies the checksum of a file using the provided MessageDigest and compares it
     * with an expected digest string.
     *
     * @param file The file whose checksum is to be verified.
     * @param expectedDigest The expected checksum as a hexadecimal string.
     * @param digest An instance of MessageDigest to compute the file's checksum.
     * @throws IOException If an I/O error occurs while reading the file.
     * @throws MojoFailureException If the computed checksum does not match the expected checksum.
     */
    private static void verifyChecksum(
        final File file, final String expectedDigest, final MessageDigest digest
    ) throws IOException, MojoFailureException {
        final String actualDigestHex = Checksums.computeChecksumAsString(file, digest);
        if (!actualDigestHex.equals(expectedDigest)) {
            throw new MojoFailureException(
                String.format(
                    "Not same digest as expected: expected <%s> was <%s>",
                    expectedDigest, actualDigestHex
                )
            );
        }
    }

    /**
     * Computes the checksum of a given file using the provided MessageDigest instance
     * and returns it as a hexadecimal string.
     *
     * @param file The file whose checksum is to be computed.
     * @param digest An instance of MessageDigest to compute the file's checksum.
     * @return The computed checksum as a hexadecimal string.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    private static String computeChecksumAsString(final File file, final MessageDigest digest)
        throws IOException {
        final InputStream fis = Files.newInputStream(file.toPath());
        final byte[] buffer = new byte[Checksums.KBYTE];
        int numRead;
        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                digest.update(buffer, 0, numRead);
            }
        } while (numRead != -1);
        fis.close();
        final byte[] actualDigest = digest.digest();
        return new String(Hex.encodeHex(actualDigest));
    }
}
