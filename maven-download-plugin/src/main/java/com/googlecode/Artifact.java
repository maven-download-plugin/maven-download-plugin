/**
 * 
 */
package com.googlecode;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * This mojo is designed to download a maven artifact from the repository and
 * download them in the specified path. The maven artifact downloaded can also
 * download it's dependency or not, based on a parameter.
 * 
 * @goal artifact
 * @phase process-resources
 * 
 * @author Marc-Andre Houle
 * 
 */
public class Artifact extends AbstractMojo {
	/**
	 * The artifact Id of the file to download.
	 * 
	 * @parameter expression="${download.artifact.artifactId}"
	 * @required
	 */
	private String artifactId;

	/**
	 * Location of the file.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		// TODO Auto-generated method stub

	}

}
