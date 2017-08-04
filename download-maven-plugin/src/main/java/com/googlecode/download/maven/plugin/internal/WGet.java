/**
 * Copyright 2009-2016 Marc-Andre Houle and Red Hat Inc Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.googlecode.download.maven.plugin.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;

/**
 * Will download a file from a web site using the standard HTTP protocol.
 *
 * @author Marc-Andre Houle
 * @author Mickael Istria (Red Hat Inc)
 */
@Mojo(name = "wget", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = false)
public class WGet extends AbstractMojo {

  /**
   * Represent the URL to fetch information from.
   */
  @Parameter(property = "download.url", required = true)
  private String url;

  /**
   * Flag to overwrite the file by redownloading it
   */
  @Parameter(property = "download.overwrite")
  private boolean overwrite;

  /**
   * Represent the file name to use as output value. If not set, will use last segment of "url"
   */
  @Parameter(property = "download.outputFileName")
  private String outputFileName;

  @Parameter()
  private String base64;

  /**
   * Represent the directory where the file should be downloaded.
   */
  @Parameter(property = "download.outputDirectory", defaultValue = "${project.build.directory}", required = true)
  private File outputDirectory;

  /**
   * The md5 of the file. If set, file signature will be compared to this signature and plugin will
   * fail.
   */
  @Parameter
  private String md5;

  /**
   * The sha1 of the file. If set, file signature will be compared to this signature and plugin will
   * fail.
   */
  @Parameter
  private String sha1;

  /**
   * The sha512 of the file. If set, file signature will be compared to this signature and plugin
   * will fail.
   */
  @Parameter
  private String sha512;

  /**
   * Whether to unpack the file in case it is an archive (.zip)
   */
  @Parameter(defaultValue = "false")
  private boolean unpack;

  /**
   * Custom username for the download
   */
  @Parameter
  private String username;

  /**
   * Custom password for the download
   */
  @Parameter
  private String password;

  /**
   * How many retries for a download
   */
  @Parameter(defaultValue = "2")
  private int retries;

  /**
   * Read timeout for a download in milliseconds
   */
  @Parameter(defaultValue = "0")
  private int readTimeOut;

  /**
   * Download file without polling cache
   */
  @Parameter(defaultValue = "false")
  private boolean skipCache;

  /**
   * The directory to use as a cache. Default is ${local-repo}/.cache/maven-download-plugin
   */
  @Parameter(property = "download.cache.directory")
  private File cacheDirectory;

  /**
   * Flag to determine whether to fail on an unsuccessful download.
   */
  @Parameter(defaultValue = "true")
  private boolean failOnError;

  /**
   * Whether to skip execution of Mojo
   */
  @Parameter(property = "download.plugin.skip", defaultValue = "false")
  private boolean skip;

  /**
   * Whether to check the signature of existing files
   */
  @Parameter(property = "checkSignature", defaultValue = "false")
  private boolean checkSignature;

  @Parameter(property = "session")
  private MavenSession session;

  @Component
  private ArchiverManager archiverManager;

  /**
   * For transfers
   */
  @Component
  private WagonManager wagonManager;

  /**
   * Method call whent he mojo is executed for the first time.
   *
   * @throws MojoExecutionException if an error is occuring in this mojo.
   * @throws MojoFailureException if an error is occuring in this mojo.
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (this.skip) {
      getLog().info("maven-download-plugin:wget skipped");
      return;
    }

    if (retries < 1) {
      throw new MojoFailureException("retries must be at least 1");
    }

    // PREPARE
    if (this.outputFileName == null) {
      try {
        this.outputFileName = new File(new URL(this.url).getFile()).getName() + ".zip";
      } catch (Exception ex) {
        throw new MojoExecutionException("Invalid URL", ex);
      }
    }
    if (this.cacheDirectory == null) {
      this.cacheDirectory = new File(this.session.getLocalRepository()
          .getBasedir(), ".cache/download-maven-plugin");
    }
    getLog().debug("Cache is: " + this.cacheDirectory.getAbsolutePath());
    DownloadCache cache = new DownloadCache(this.cacheDirectory);
    this.outputDirectory.mkdirs();
    File outputFile = new File(this.outputDirectory, this.outputFileName);

    // DO
    try {
      boolean haveFile = outputFile.exists();
      if (haveFile) {
        boolean signatureMatch = true;
        if (this.checkSignature) {
          String expectedDigest = null, algorithm = null;
          if (this.md5 != null) {
            expectedDigest = this.md5;
            algorithm = "MD5";
          }

          if (this.sha1 != null) {
            expectedDigest = this.sha1;
            algorithm = "SHA1";
          }

          if (this.sha512 != null) {
            expectedDigest = this.sha512;
            algorithm = "SHA-512";
          }

          if (expectedDigest != null) {
            try {
              SignatureUtils.verifySignature(outputFile, expectedDigest, MessageDigest.getInstance(algorithm));
            } catch (MojoFailureException e) {
              getLog()
                  .warn("The local version of file " + outputFile.getName() + " doesn't match the expected signature. "
                      + "You should consider checking the specified signature is correctly set.");
              signatureMatch = false;
            }
          }
        }

        // TODO verify last modification date
        if (!signatureMatch) {
          outputFile.delete();
          haveFile = false;
        } else if (!overwrite) {
          getLog().info("File already exist, skipping");
        }
      }

      if (!haveFile) {
        File cached = cache.getArtifact(this.url, this.md5, this.sha1, this.sha512);
        if (!this.skipCache && cached != null && cached.exists()) {
          getLog().info("Got from cache: " + cached.getAbsolutePath());
          Files.copy(cached.toPath(), outputFile.toPath());
        } else {
          boolean done = false;
          while (!done && this.retries > 0) {
            try {
              doGet(outputFile);
              if (this.md5 != null) {
                SignatureUtils.verifySignature(outputFile, this.md5, MessageDigest.getInstance("MD5"));
              }
              if (this.sha1 != null) {
                SignatureUtils.verifySignature(outputFile, this.sha1, MessageDigest.getInstance("SHA1"));
              }
              if (this.sha512 != null) {
                SignatureUtils.verifySignature(outputFile, this.sha512, MessageDigest.getInstance("SHA-512"));
              }
              done = true;
            } catch (Exception ex) {
              getLog().warn("Could not get content", ex);
              this.retries--;
              if (this.retries > 0) {
                getLog().warn("Retrying (" + this.retries + " more)");
              }
            }
          }
          if (!done) {
            if (failOnError) {
              throw new MojoFailureException("Could not get content");
            } else {
              getLog().warn("Not failing download despite download failure.");
              return;
            }
          }
        }
      }
      cache.install(this.url, outputFile, this.md5, this.sha1, this.sha512);
      if (this.unpack) {
        unpack(outputFile);
      }
    } catch (Exception ex) {
      throw new MojoExecutionException("IO Error", ex);
    }
  }

  private void unpack(File outputFile) throws NoSuchArchiverException {
    UnArchiver unarchiver = this.archiverManager.getUnArchiver(outputFile);
    unarchiver.setSourceFile(outputFile);
    unarchiver.setDestDirectory(this.outputDirectory);
    unarchiver.extract();
    outputFile.delete();
  }

  private void doGet(File outputFile) throws Exception {
    String urlStr = this.url;

    try {

      GetRequest request = Unirest.get(urlStr);
      if (this.base64 != null)
        request.header("Authorization", "Basic " + this.base64);

      InputStream file = request.asBinary()
          .getBody();

      OutputStream fop = new FileOutputStream(outputFile, false);
      int c;
      while ((c = file.read()) != -1)
        fop.write(c);

      fop.close();

    } catch (UnirestException | IOException e) {
      getLog().error("Unable to download file");
      e.printStackTrace();
    }
  }
}
