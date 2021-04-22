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
package com.googlecode.download.maven.plugin.internal;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.net.ssl.SSLContext;

/**
 * Helper class to check supported SSL protocols.
 */
public final class SSLProtocols {

    /**
     * Default SSL protocols supported by the plugin.
     */
    private static final String[] DEFAULT_PROTOCOLS = {
        "SSLv3","TLSv1","TLSv1.1","TLSv1.2"
    };

    /**
     * Default SSL protocols supported by the plugin (including {@code TLS1.3}).
     */
    private static final String[] WITH_1_3_PROTOCOLS = {
        "SSLv3","TLSv1","TLSv1.1","TLSv1.2", "TLSv1.3"
    };

    /**
     * List SSL protocols supported by the plugin.
     * @return The list of supported SSL protocols.
     */
    public static String[] supported() {
        return SSLProtocols.isTls13Supported() ?
            SSLProtocols.WITH_1_3_PROTOCOLS : SSLProtocols.DEFAULT_PROTOCOLS;
    }

    /**
     * Define runtime supports {@code TLS1.3}.
     * @return True if supports.
     */
    private static boolean isTls13Supported() {
        try {
            return Arrays.asList(
                SSLContext.getDefault().getSupportedSSLParameters()
                    .getProtocols()
            ).contains("TLSv1.3");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
