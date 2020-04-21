/*
 * Copyright 2012, Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.download.maven.plugin.internal.signature;

import com.googlecode.download.maven.plugin.internal.SignatureUtils;
import java.io.File;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.plugin.logging.Log;

/**
 * Signatures supplied to verify file integrity.
 * @author Paul Polishchuk
 */
public final class Signatures {

    private final Map<Signature, String> supplied;

    public Signatures(
        @Nullable final String md5, @Nullable final String sha1,
        @Nullable final String sha256, @Nullable final String sha512,
        Log log
    ) {
        this.supplied = Signatures.create(md5, sha1, sha256, sha512);
        if (this.supplied.isEmpty()) {
            log.warn("No signatures were supplied, skipping file validation");
        } else if (this.supplied.size() > 1) {
            log.warn("More than one signature is supplied. This may be slow for big files. Consider using a single signature");
        }
    }

    /**
     * Validates the file with supplied signatures.
     * @param file File to validate.
     * @return True if the file matches all supplied signatures
     *  or if no signatures were supplied.
     */
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
     * Validates the file with supplied signatures.
     * @param file File to validate.
     * @throws Exception If the file didn't match any supplied signature.
     */
    public void validate(final File file) throws Exception {
        for (final Map.Entry<Signature, String> entry : this.supplied.entrySet()) {
            SignatureUtils.verifySignature(
                file, entry.getValue(),
                MessageDigest.getInstance(entry.getKey().algo())
            );
        }
    }

    /**
     * Fill the map of signatures.
     * @param md5 Supplied md5 signature, may be {@literal null}.
     * @param sha1 Supplied sha1 signature, may be {@literal null}.
     * @param sha256 Supplied sha256 signature, may be {@literal null}.
     * @param sha512 Supplied sha512 signature, may be {@literal null}.
     * @return A map of a signature type to a digest; {@literal null} digests
     *  are not included.
     */
    private static Map<Signature, String> create(
        @Nullable final String md5, @Nullable final String sha1,
        @Nullable final String sha256, @Nullable final String sha512
    ) {
        final Map<Signature, String> digests = new EnumMap<>(Signature.class);
        if (md5 != null) {
            digests.put(Signature.MD5, md5);
        }
        if (sha1 != null) {
            digests.put(Signature.SHA1, sha1);
        }
        if (sha256 != null) {
            digests.put(Signature.SHA256, sha256);
        }
        if (sha512 != null) {
            digests.put(Signature.SHA512, sha512);
        }
        return digests;
    }
}
