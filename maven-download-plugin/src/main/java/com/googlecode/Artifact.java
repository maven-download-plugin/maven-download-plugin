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
/**
 * 
 */
package com.googlecode;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
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
	 * @parameter expression="${artifactId}"
	 * @required
	 */
	private String artifactId;
	
	/**
	 * The group Id of the file to download.
	 * 
	 * @parameter expression="${groupId}"
	 * @required
	 */
	private String groupId;
	
	/**
	 * The version of the file to download.
	 * 
	 * @parameter expression="${version}"
	 * @required
	 */
	private String version;
	
	/**
	 * The type of artifact to download.
	 * 
	 * @parameter expression="${type}" default-value=jar
	 */
	private String type;
	
	/**
	 * The classifier of artifact to download.
	 * 
	 * @parameter expression="${classifier}"
	 */
	private String classifier;
	
	/**
	 * Location of the file.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;
	
	/** @component */
	private ArtifactFactory artifactFactory;

	/** @component */
	private ArtifactResolver resolver;

	/**@parameter expression="${localRepository}" */
	private ArtifactRepository localRepository;

	/** @parameter expression="${project.remoteArtifactRepositories}" */
	private java.util.List remoteRepositories;


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		org.apache.maven.artifact.Artifact artifact = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
		try {
			resolver.resolve( artifact, remoteRepositories, localRepository );
		} catch (ArtifactResolutionException e) {
			getLog().debug("Artifact could not be resolved.", e);
			throw new MojoFailureException("Artifact could not be resolved.");
		} catch (ArtifactNotFoundException e) {
			getLog().debug("Artifact could not be found.", e);
			throw new MojoFailureException("Artifact could not be found.");
		}
		File toCopy = artifact.getFile();
		if(toCopy != null && toCopy.exists() && toCopy.isFile()){
			try {
				getLog().info("Copying file " + toCopy.getName() + " to directory " + outputDirectory);
				FileUtils.copyFileToDirectory(toCopy, outputDirectory);
			} catch (IOException e) {
				getLog().debug("Error while copying file", e);
				throw new MojoFailureException("Error copying the file : " + e.getMessage());
			}
		}else{
			throw new MojoFailureException("Artifact file not present : " + toCopy);
		}
	}

}
