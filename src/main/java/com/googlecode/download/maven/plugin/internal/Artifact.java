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
package com.googlecode.download.maven.plugin.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

/**
 * This mojo is designed to download a maven artifact from the repository and
 * download them in the specified path. The maven artifact downloaded can also
 * download it's dependency or not, based on a parameter.
 * @author Marc-Andre Houle
 */
@Mojo(name = "artifact", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = false)
public class Artifact extends AbstractMojo {
    /**
     * The artifact Id of the file to download.
     */
    @Parameter(property = "artifactId", required = true)
    private String artifactId;

    /**
     * The group Id of the file to download.
     */
    @Parameter(property = "groupId", required = true)
    private String groupId;

    /**
     * The version of the file to download.
     */
    @Parameter(property = "version", required = true)
    private String version;

    /**
     * The type of artifact to download.
     */
    @Parameter(property = "type", defaultValue = "jar")
    private String type;

    /**
     * The classifier of artifact to download.
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * Location of the file.
     * @parameter expression="${outputDirectory}"
     * default-value="${project.build.directory}"
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}")
    private File outputDirectory;

    /**
     * Will set the output file name to the specified name.  Valid only when the dependency depth
     * is set to 0.
     * @parameter expression="${outputFileName}"
     */
    @Parameter(property = "outputFileName")
    private String outputFileName;

    /**
     * Whether to unpack the artifact
     */
    @Parameter(property = "unpack", defaultValue = "false")
    private boolean unpack;

    /**
     * Whether to skip execution of Mojo
     */
    @Parameter(property = "download.plugin.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The dependency depth to query. Will try to fetch the artifact for as much
     * as the number of dependency specified.
     */
    @Parameter(property = "dependencyDepth", defaultValue = "0")
    private long dependencyDepth;

    @Parameter(property = "project.remoteArtifactRepositories")
    private List remoteRepositories;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver resolver;

    @Component
    private ArtifactMetadataSource metadataSource;

    @Component
    private MavenProjectBuilder mavenProjectBuilder;

    @Component
    private ArchiverManager archiverManager;

    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    private final Set<org.apache.maven.artifact.Artifact> artifactToCopy = new HashSet<org.apache.maven.artifact.Artifact>();

    /**
     * Will download the specified artifact in the specified directory.
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            getLog().info("maven-download-plugin:artifact skipped");
            return;
        }
        if (this.dependencyDepth > 0 && this.outputFileName != null) {
            throw new MojoFailureException("Cannot have a dependency depth higher than 0 and an outputFileName");
        }
        org.apache.maven.artifact.Artifact artifact = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
        downloadAndAddArtifact(artifact, dependencyDepth);
        createOutputDirectoryIfNecessary();
        for (org.apache.maven.artifact.Artifact copy : this.artifactToCopy) {
            if (this.unpack) {
                this.unpackFileToDirectory(copy);
            } else {
                this.copyFileToDirectory(copy);
            }
        }
    }

    /**
     * Download the artifact when possible and copy it to the target directory
     * and will fetch the dependency until the specified depth is reached.
     * @param artifact The artifact to download and set.
     * @param dependencyDepth2 The depth that will be downloaded for the dependencies.
     */
    private void downloadAndAddArtifact(org.apache.maven.artifact.Artifact artifact, long dependencyDepth2) throws MojoFailureException {
        this.downloadArtifact(artifact);
        this.artifactToCopy.add(artifact);
        if (dependencyDepth > 0) {
            Set<org.apache.maven.artifact.Artifact> dependencies = getTransitiveDependency(artifact);
            getLog().debug("Nummber dependencies : " + dependencies.size());
            for (org.apache.maven.artifact.Artifact dependency : dependencies) {
                downloadAndAddArtifact(dependency, dependencyDepth2 - 1);
            }
        }
    }

    /**
     * Will check if the artifact is in the local repository and download it if
     * it is not.
     * @param artifact The artifact to check if it is present in the local directory.
     * @throws MojoFailureException If an error happen while resolving the artifact.
     */
    private void downloadArtifact(org.apache.maven.artifact.Artifact artifact) throws MojoFailureException {
        try {
            resolver.resolve(artifact, remoteRepositories, localRepository);
        } catch (ArtifactResolutionException e) {
            getLog().debug("Artifact could not be resolved.", e);
            throw new MojoFailureException("Artifact could not be resolved.");
        } catch (ArtifactNotFoundException e) {
            getLog().debug("Artifact could not be found.", e);
            throw new MojoFailureException("Artifact could not be found.");
        }
    }

    /**
     * Will copy the specified artifact into the output directory.
     * @param artifact The artifact already resolved to be copied.
     * @throws MojoFailureException If an error happened while copying the file.
     */
    private void copyFileToDirectory(org.apache.maven.artifact.Artifact artifact) throws MojoFailureException {
        File toCopy = artifact.getFile();
        if (toCopy != null && toCopy.exists() && toCopy.isFile()) {
            try {
                getLog().info("Copying file " + toCopy.getName() + " to directory " + outputDirectory);
                File outputFile = null;
                if (this.outputFileName == null) {
                    outputFile = new File(outputDirectory, toCopy.getName());
                } else {
                    outputFile = new File(outputDirectory, this.outputFileName);
                }
                Files.copy(toCopy.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLog().debug("Error while copying file", e);
                throw new MojoFailureException("Error copying the file : " + e.getMessage());
            }
        } else {
            throw new MojoFailureException("Artifact file not present : " + toCopy);
        }
    }

    private void unpackFileToDirectory(org.apache.maven.artifact.Artifact artifact) throws MojoExecutionException {
        File toUnpack = artifact.getFile();
        if (toUnpack != null && toUnpack.exists() && toUnpack.isFile()) {
            try {
                UnArchiver unarchiver = this.archiverManager.getUnArchiver(toUnpack);
                unarchiver.setSourceFile(toUnpack);
                unarchiver.setDestDirectory(this.outputDirectory);
                unarchiver.extract();
            } catch (Exception ex) {
                throw new MojoExecutionException("Issue while unarchiving", ex);
            }
        }
    }

    private void createOutputDirectoryIfNecessary() {
        if (this.outputDirectory != null && !this.outputDirectory.exists()) {
            this.outputDirectory.mkdirs();
        }
    }

    /**
     * Will fetch a list of all the transitive dependencies for an artifact and
     * return a set of those artifacts.
     * @param artifact The artifact for which transitive dependencies need to be
     * downloaded.
     * @return The set of dependencies that was dependant.
     * @throws MojoFailureException If anything goes wrong when getting transitive dependency.
     * Note : Suppress warning used for the uncheck cast of artifact
     * set.
     */
    @SuppressWarnings("unchecked")
    private Set<org.apache.maven.artifact.Artifact> getTransitiveDependency(org.apache.maven.artifact.Artifact artifact) throws MojoFailureException {
        try {
            org.apache.maven.artifact.Artifact pomArtifact = artifactFactory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact
                .getVersion(), artifact.getClassifier(), "pom");
            MavenProject pomProject = mavenProjectBuilder.buildFromRepository(pomArtifact, this.remoteRepositories, this.localRepository);
            Set<org.apache.maven.artifact.Artifact> dependents = pomProject.createArtifacts(this.artifactFactory, null, null);
            ArtifactResolutionResult result = resolver.resolveTransitively(dependents, pomArtifact, this.localRepository, this.remoteRepositories,
                this.metadataSource, null);
            if (result != null) {
                getLog().debug("Found transitive dependency : " + result);
                return result.getArtifacts();
            }
        } catch (ArtifactResolutionException e) {
            getLog().debug("Could not resolved the dependency", e);
            throw new MojoFailureException("Could not resolved the dependency : " + e.getMessage());
        } catch (ArtifactNotFoundException e) {
            getLog().debug("Could not find the dependency", e);
            throw new MojoFailureException("Could not find the dependency : " + e.getMessage());
        } catch (ProjectBuildingException e) {
            getLog().debug("Error Creating the pom project for artifact : " + artifact, e);
            throw new MojoFailureException("Error getting transitive dependencies : " + e.getMessage());
        }
        return null;
    }
}
