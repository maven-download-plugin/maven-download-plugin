/**
 * Copyright 2012, Red Hat Inc
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
package com.googlecode.download.maven.plugin.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;
import org.apache.maven.plugin.MojoFailureException;

/**
 * @author Mickael Istria (Red Hat Inc)
 */
public class ChecksumUtils {

    public static void verifyChecksum(File file, String expectedDigest, MessageDigest digest) throws Exception {
        String actualDigestHex = ChecksumUtils.computeChecksumAsString(file, digest);
        if (!actualDigestHex.equals(expectedDigest)) {
            throw new MojoFailureException("Not same digest as expected: expected <" + expectedDigest + "> was <" + actualDigestHex + ">");
        }
    }

    public static String computeChecksumAsString(File file,
        MessageDigest digest) throws IOException {
        InputStream fis = Files.newInputStream(file.toPath());
        byte[] buffer = new byte[1024];
        int numRead;
        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                digest.update(buffer, 0, numRead);
            }
        } while (numRead != -1);
        fis.close();
        byte[] actualDigest = digest.digest();
        return new String(Hex.encodeHex(actualDigest));
    }

    public static String getMD5(File file) throws IOException, NoSuchAlgorithmException {
        return computeChecksumAsString(file, MessageDigest.getInstance("MD5"));
    }

    public static String getSHA1(File file) throws IOException, NoSuchAlgorithmException {
        return computeChecksumAsString(file, MessageDigest.getInstance("SHA1"));
    }

    public static String getSHA256(File file) throws IOException, NoSuchAlgorithmException {
        return computeChecksumAsString(file, MessageDigest.getInstance("SHA-256"));
    }

    public static String getSHA512(File file) throws IOException, NoSuchAlgorithmException {
        return computeChecksumAsString(file, MessageDigest.getInstance("SHA-512"));
    }
}
