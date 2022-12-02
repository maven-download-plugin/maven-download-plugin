/**
 * Copyright [2009] Marc-Andre Houle
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.download.maven.plugin.internal;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.apache.maven.RepositoryUtils.toArtifact;

/**
 * This mojo is designed to download a maven artifact from the repository and
 * download them in the specified path. The maven artifact downloaded can also
 * download it's dependency or not, based on a parameter.
 *
 * @author Marc-Andre Houle
 */
@Mojo(name = "artifact", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = false)
public class ArtifactMojo extends AbstractMojo {
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
     *
     * @parameter expression="${outputDirectory}"
     * default-value="${project.build.directory}"
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}")
    private File outputDirectory;

    /**
     * Will set the output file name to the specified name.  Valid only when the dependency depth
     * is set to 0.
     *
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

    /**
     * The Maven Session.
     */
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    @Inject
    private ArtifactFactory artifactFactory;

    @Inject
    private ArchiverManager archiverManager;

    /**
     * The (injected) {@link RepositorySystem} instance.
     */
    @Inject
    protected RepositorySystem repositorySystem;

    /**
     * The (injected) {@link ProjectBuilder} instance.
     */
    @Inject
    protected ProjectBuilder projectBuilder;

    /**
     * Will download the specified artifact in the specified directory.
     *
     * @throws MojoExecutionException thrown if there is a problem while processing the request
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            getLog().info("maven-download-plugin:artifact skipped");
            return;
        }
        if (this.dependencyDepth > 0 && this.outputFileName != null) {
            throw new MojoExecutionException("Cannot have a dependency depth higher than 0 and an outputFileName");
        }
        final Artifact artifact = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, type,
                classifier);
        createOutputDirectoryIfNecessary();
        try {
            downloadAndAddArtifact(artifact, dependencyDepth)
                    .thenAccept(artifacts -> artifacts.forEach(copy -> {
                        try {
                            if (this.unpack) {
                                this.unpackFileToDirectory(copy);
                            } else {
                                this.copyFileToDirectory(copy);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException();
                        }
                    })).toCompletableFuture()
                    .get();
        } catch (Exception e) {
            throw new MojoExecutionException("Abnormal termination of the retrieval", e);
        }
    }

    /**
     * Download the artifact when possible and copy it to the target directory
     * and will fetch the dependency until the specified depth is reached.
     *
     * @param artifact The artifact to download and set.
     * @param maxDepth The depth that will be downloaded for the dependencies.
     * @return completion stage which, when complete, conains a set of resolved dependency artifacts
     */
    private CompletionStage<Set<Artifact>> downloadAndAddArtifact(Artifact artifact, long maxDepth) {
        return this.downloadArtifact(artifact)
                .thenApply(downloadedArtifact -> {
                    Set<Artifact> result = new HashSet<>();
                    result.add(downloadedArtifact);
                    if (maxDepth > 0) {
                        this.resolveDependencyArtifacts(downloadedArtifact)
                                .forEach(completionStage -> {
                                    try {
                                        completionStage.thenCompose(artifact1 -> downloadAndAddArtifact(artifact1,
                                                        maxDepth - 1))
                                                    .toCompletableFuture()
                                                    .thenAccept(result::addAll)
                                                .get();
                                    } catch (InterruptedException | ExecutionException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                    return result;
                });
    }

    /**
     * Will fetch a list of all the transitive dependencies for an artifact and
     * return a list of completion stages, which will contain the downloaded
     * artifacts when complete
     *
     * @param artifact The artifact for which transitive dependencies need to be
     *                 downloaded.
     * @return list of completion stages, which will contain the downloaded
     * artifacts when complete
     */
    private List<CompletionStage<Artifact>> resolveDependencyArtifacts(Artifact artifact) {
        final Artifact pomArtifact = artifactFactory.createProjectArtifact(artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion());
        if (getLog().isDebugEnabled()) {
            getLog().debug(String.format("Resolving dependencies for artifact %s...", artifact.getId()));
        }
        try {
            final ProjectBuildingResult result = projectBuilder.build(pomArtifact, false,
                    new DefaultProjectBuildingRequest() {{
                        setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
                        setResolveDependencies(false);
                        setLocalRepository(session.getLocalRepository());
                        setRemoteRepositories(session.getCurrentProject().getRemoteArtifactRepositories());
                        setUserProperties(session.getUserProperties());
                        setSystemProperties(session.getSystemProperties());
                        setActiveProfileIds(session.getRequest().getActiveProfiles());
                        setInactiveProfileIds(session.getRequest().getInactiveProfiles());
                        setRepositorySession(session.getRepositorySession());
                        setBuildStartTime(session.getStartTime());
                    }});
            return result.getProject().getDependencies().stream()
                    .map(this::createDependencyArtifact)
                    .map(this::downloadArtifact)
                    .collect(Collectors.toList());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Artifact createDependencyArtifact(Dependency d) {
        try {
            return artifactFactory.createDependencyArtifact(d.getGroupId(),
                    d.getArtifactId(),
                    VersionRange.createFromVersionSpec(d.getVersion()),
                    d.getType(),
                    d.getClassifier(),
                    d.getScope());
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Downloads the given dependency artifact in a separate thread.
     *
     * @param artifact artifact to be downloaded
     * @return completion stage which contains the given artifact when complete
     */
    private CompletionStage<Artifact> downloadArtifact(Artifact artifact) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final ArtifactResult artifactResult = repositorySystem.resolveArtifact(session.getRepositorySession(),
                        new ArtifactRequest(toArtifact(artifact),
                                session.getCurrentProject().getRemoteProjectRepositories(),
                                getClass().getName()));
                artifact.setFile(artifactResult.getArtifact().getFile());
                artifact.setVersion(artifactResult.getArtifact().getVersion());
                artifact.setResolved(artifactResult.isResolved());
                return artifact;
            }
            catch (ArtifactResolutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Will copy the specified artifact into the output directory.
     *
     * @param artifact The artifact already resolved to be copied.
     * @throws MojoFailureException If an error happened while copying the file.
     */
    private void copyFileToDirectory(Artifact artifact) throws MojoFailureException {
        if (artifact.getFile() == null || !artifact.getFile().exists() || !artifact.getFile().isFile()) {
            throw new MojoFailureException("Artifact file not resolved for artifact: "
                    + artifact.getId());
        }

        try {
            File outputFile = new File(outputDirectory, Optional.ofNullable(this.outputFileName)
                    .orElse(artifact.getFile().getName()));
            Files.copy(artifact.getFile().toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoFailureException("Error copying the file : " + e.getMessage());
        }
    }

    private void unpackFileToDirectory(Artifact artifact) throws NoSuchArchiverException {
        final File toUnpack = artifact.getFile();
        if (toUnpack != null && toUnpack.exists() && toUnpack.isFile()) {
            UnArchiver unarchiver = this.archiverManager.getUnArchiver(toUnpack);
            unarchiver.setSourceFile(toUnpack);
            unarchiver.setDestDirectory(this.outputDirectory);
            unarchiver.extract();
        }
    }

    private void createOutputDirectoryIfNecessary() {
        if (this.outputDirectory != null && !this.outputDirectory.exists()) {
            this.outputDirectory.mkdirs();
        }
    }
}
