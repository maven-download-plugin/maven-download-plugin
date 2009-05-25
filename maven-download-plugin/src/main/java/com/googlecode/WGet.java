/**
 * Copyright [2009] Marc-Andre Houle
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
package com.googlecode;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
  * Will download a file from a web site using the standard HTTP protocol.
  * @goal wget 
  * @phase process-resources
  * @requiresProject false
  * 
  * @author Marc-Andre Houle
  */

public class WGet extends AbstractMojo{
	
	/**
	  * Represent the URL to fetch information from.
	  * @parameter expression="${url}" 
	  * @required
	  */
	private String url;
	
	/**
	  * Represent the file name to use as output value.
	  * @parameter expression="${outputFileName}"
	  * @required
	  */
	private String outputFileName;
	
	/**
	  * Represent the directory where the file should be downloaded.
	  * @parameter expression="${targetDirectory}"  default-value="${project.build.directory}"
	  */
	private String targetDirectory;
	
	/**
	  * Method call whent he mojo is executed for the first time.
	  * @throws MojoExecutionException if an error is occuring in this mojo.
	  * @throws MojoFailureException if an error is occuring in this mojo.
	  */
	public void execute() throws MojoExecutionException, MojoFailureException {
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet httpget = new HttpGet(this.url); 
		try {
			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
			// If the response does not enclose an entity, there is no need
			// to worry about connection release
			if (entity != null) {
				InputStream inStream = entity.getContent();
				try{
					OutputStream outStream = FileUtils.openOutputStream(new File(this.targetDirectory, this.outputFileName));
					IOUtils.copy(inStream, outStream);
				}finally{
					// Closing the input stream will trigger connection release
					inStream.close();
				}
			}
		} catch (IOException ex) {
			// In case of an IOException the connection will be released
			// back to the connection manager automatically
			throw new MojoExecutionException("Error while copying value.", ex);
		} catch (RuntimeException ex) {
			// In case of an unexpected exception you may want to abort
			// the HTTP request in order to shut down the underlying 
			// connection and release it back to the connection manager.
			httpget.abort();
			throw new MojoFailureException("Interuption caused cancelled download.");
		}
	}
}
