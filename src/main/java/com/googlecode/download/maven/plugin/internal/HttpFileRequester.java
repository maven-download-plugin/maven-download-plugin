/**
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.maven.plugin.logging.Log;

public class HttpFileRequester {

    private static final long KBYTE = 1024L;
    private static final char K_UNIT = 'K';
    private static final char B_UNIT = 'b';

    private final CloseableHttpClient httpClient;
    private final Log log;

    public HttpFileRequester(final CloseableHttpClient httpClient, final Log log) {
        this.httpClient = httpClient;
        this.log = log;
    }

    public void download(final URI uri, final File outputFile, final HttpContext clientContext) throws Exception {
        final HttpGet httpGet = new HttpGet(uri);
        httpClient.execute(httpGet, new ResponseHandler<Void>() {

            @Override
            public Void handleResponse(final HttpResponse response) throws IOException {
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    if (log != null) {
                        log.info(String.format( "%s: %s", "Downloading", uri));
                    }

                    final long totalLength = entity.getContentLength();
                    final char unit = totalLength >= KBYTE ? K_UNIT : B_UNIT;
                    long completed = 0L;

                    byte[] tmp = new byte[8 * (int) KBYTE];
                    try (InputStream in = entity.getContent(); FileOutputStream out = new FileOutputStream(outputFile)) {
                        int bytesRead;
                        while ((bytesRead = in.read(tmp)) != -1) {
                            out.write(tmp, 0, bytesRead);

                            if (log != null) {
                                completed += bytesRead;
                                final String totalInUnits;
                                final long completedInUnits;
                                if (unit == K_UNIT) {
                                    totalInUnits = totalLength == -1 ? "?" : Long.toString(totalLength / KBYTE) + unit;
                                    completedInUnits = completed / KBYTE;
                                } else {
                                    totalInUnits = totalLength == -1 ? "?" : Long.toString(totalLength);
                                    completedInUnits = completed;
                                }
                                log.info(String.format("%d/%s", completedInUnits, totalInUnits));
                            }
                        }
                        out.flush();

                        if (log != null) {
                            log.info(String.format("%s %s",
                                    "downloaded",
                                    unit == K_UNIT ? Long.toString(completed / KBYTE) + unit : Long.toString(completed)));
                        }

                    } catch (IOException ex) {
                        if (log != null) {
                            log.error(ex);
                        }
                        throw ex;
                    }
                }
                return null;
            }

        }, clientContext);
    }

}
