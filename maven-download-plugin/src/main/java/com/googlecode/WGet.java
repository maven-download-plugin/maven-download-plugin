package com.googlecode;

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
	  * Method call whent he mojo is executed for the first time.
	  * @throws MojoExecutionException if an error is occuring in this mojo.
	  * @throws MojoFailureException if an error is occuring in this mojo.
	  */
	public void execute() throws MojoExecutionException, MojoFailureException {
			getLog().info("Hello world!");
	}
}
