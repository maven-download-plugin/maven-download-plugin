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
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;

/**
 * File requester that can download resources over HTTP transport using Apache HttpClient 4.x.
 */
public class HttpFileRequester {

    private final CloseableHttpClient httpClient;
    private final ProgressReport progressReport;

    public HttpFileRequester(final CloseableHttpClient httpClient, final ProgressReport progressReport) {
        this.httpClient = httpClient;
        this.progressReport = progressReport;
    }

    /**
     * Downloads the resource with the given URI to the specified local file system location.
     *
     * @param uri the target URI
     * @param outputFile the output file
     * @param clientContext the HTTP execution context.
     */
    public void download(final URI uri, final File outputFile, final HttpContext clientContext, List<Header> headers) throws Exception {
        final HttpGet httpGet = new HttpGet(uri);
        headers.forEach(httpGet::setHeader);
        httpClient.execute(httpGet, new ResponseHandler<Void>() {

            @Override
            public Void handleResponse(final HttpResponse response) throws IOException {
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    progressReport.initiate(uri, entity.getContentLength());

                    byte[] tmp = new byte[8 * 11024];
                    try (InputStream in = entity.getContent(); OutputStream out = new FileOutputStream(outputFile)) {
                        int bytesRead;
                        while ((bytesRead = in.read(tmp)) != -1) {
                            out.write(tmp, 0, bytesRead);
                            progressReport.update(bytesRead);
                        }
                        out.flush();
                        progressReport.completed();

                    } catch (IOException ex) {
                        progressReport.error(ex);
                        throw ex;
                    }
                }
                return null;
            }

        }, clientContext);
    }

}
