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
import javax.inject.Inject;
import org.apache.maven.RepositoryUtils;
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
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * This mojo is designed to download a maven artifact from the repository and
 * download them in the specified path. The maven artifact downloaded can also
 * download its dependency or not, based on a parameter.
 * @author Marc-Andre Houle
 */
@Mojo(name = "artifact", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = false)
public final class ArtifactMojo extends AbstractMojo {
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
     * Whether to unpack the artifact.
     */
    @Parameter(property = "unpack", defaultValue = "false")
    private boolean unpack;

    /**
     * Whether to skip execution of Mojo.
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
    private MavenSession session;

    /**
     * An instance of {@link ArtifactFactory} that is injected to manage the creation
     * and manipulation of Maven artifacts.
     */
    @Inject
    private ArtifactFactory artifactFactory;

    /**
     * Manages the archiving and unarchiving operations for artifacts.
     */
    @Inject
    private ArchiverManager archiverManager;

    /**
     * The (injected) {@link RepositorySystem} instance.
     */
    @Inject
    private RepositorySystem repositorySystem;

    /**
     * The (injected) {@link ProjectBuilder} instance.
     */
    @Inject
    private ProjectBuilder projectBuilder;

    /**
     * Will download the specified artifact in the specified directory.
     * @throws MojoExecutionException thrown if there is a problem while processing the request
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            this.getLog().info("download-maven-plugin:artifact skipped");
            return;
        }
        if (this.dependencyDepth > 0 && this.outputFileName != null) {
            throw new MojoExecutionException(
                "Cannot have a dependency depth higher than 0 and an outputFileName"
            );
        }
        final Artifact artifact = this.artifactFactory.createArtifactWithClassifier(
            this.groupId, this.artifactId, this.version, this.type, this.classifier
        );
        this.createOutputDirectoryIfNecessary();
        try {
            this.downloadAndAddArtifact(artifact, this.dependencyDepth)
                .thenAccept(
                    artifacts -> artifacts.forEach(
                        copy -> {
                            try {
                                if (this.unpack) {
                                    this.unpackFileToDirectory(copy);
                                } else {
                                    this.copyFileToDirectory(copy);
                                }
                            } catch (final NoSuchArchiverException | MojoFailureException exc) {
                                throw new RuntimeException(exc);
                            }
                        }
                    )
                )
                .toCompletableFuture()
                .get();
        } catch (final InterruptedException | ExecutionException exc) {
            throw new MojoExecutionException("Abnormal termination of the retrieval", exc);
        }
    }

    /**
     * Download the artifact when possible and copy it to the target directory
     * and will fetch the dependency until the specified depth is reached.
     * @param artifact The artifact to download and set.
     * @param maxDepth The depth that will be downloaded for the dependencies.
     * @return Completion stage which, when complete, conains a set of resolved dependency artifacts
     */
    private CompletionStage<Set<Artifact>> downloadAndAddArtifact(
        final Artifact artifact, final long maxDepth
    ) {
        return this.downloadArtifact(artifact)
            .thenApply(
                downloadedArtifact -> {
                    final Set<Artifact> result = new HashSet<>();
                    result.add(downloadedArtifact);
                    if (maxDepth > 0L) {
                        this.resolveDependencyArtifacts(downloadedArtifact)
                            .forEach(
                                completionStage -> {
                                    try {
                                        completionStage.thenCompose(
                                            artifact1 -> this.downloadAndAddArtifact(
                                                artifact1, maxDepth - 1L
                                            )
                                        )
                                            .toCompletableFuture()
                                            .thenAccept(result::addAll)
                                            .get();
                                    } catch (final InterruptedException | ExecutionException exc) {
                                        throw new RuntimeException(exc);
                                    }
                                }
                            );
                    }
                    return result;
                }
            );
    }

    /**
     * Will fetch a list of all the transitive dependencies for an artifact and return a list
     * of completion stages, which will contain the downloaded artifacts when complete.
     * @param artifact The artifact for which transitive dependencies need to be downloaded.
     * @return List of completion stages, which will contain the downloaded artifacts when complete.
     * @checkstyle AnonInnerLength (50 lines)
     */
    private List<CompletionStage<Artifact>> resolveDependencyArtifacts(final Artifact artifact) {
        final Artifact pomArtifact = this.artifactFactory.createProjectArtifact(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            artifact.getVersion()
        );
        if (this.getLog().isDebugEnabled()) {
            this.getLog().debug(
                String.format("Resolving dependencies for artifact %s...", artifact.getId())
            );
        }
        try {
            final ProjectBuildingResult result = this.projectBuilder.build(
                pomArtifact, false,
                new DefaultProjectBuildingRequest() {
                    {
                        this.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
                        this.setResolveDependencies(false);
                        this.setLocalRepository(ArtifactMojo.this.session.getLocalRepository());
                        this.setRemoteRepositories(
                            ArtifactMojo.this.session.getCurrentProject()
                                .getRemoteArtifactRepositories()
                        );
                        this.setUserProperties(ArtifactMojo.this.session.getUserProperties());
                        this.setSystemProperties(ArtifactMojo.this.session.getSystemProperties());
                        this.setActiveProfileIds(
                            ArtifactMojo.this.session.getRequest().getActiveProfiles()
                        );
                        this.setInactiveProfileIds(
                            ArtifactMojo.this.session.getRequest().getInactiveProfiles()
                        );
                        this.setRepositorySession(ArtifactMojo.this.session.getRepositorySession());
                        this.setBuildStartTime(ArtifactMojo.this.session.getStartTime());
                    }
                }
            );
            return result.getProject().getDependencies().stream()
                .map(this::createDependencyArtifact)
                .map(this::downloadArtifact)
                .collect(Collectors.toList());
        } catch (final ProjectBuildingException exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Creates an artifact based on the given dependency information.
     * @param dep The dependency information used to create the artifact.
     * @return The created artifact.
     */
    private Artifact createDependencyArtifact(final Dependency dep) {
        try {
            return this.artifactFactory.createDependencyArtifact(
                dep.getGroupId(),
                dep.getArtifactId(),
                VersionRange.createFromVersionSpec(dep.getVersion()),
                dep.getType(),
                dep.getClassifier(),
                dep.getScope()
            );
        } catch (final InvalidVersionSpecificationException exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Downloads the given dependency artifact in a separate thread.
     * @param artifact Artifact to be downloaded
     * @return Completion stage which contains the given artifact when complete
     */
    private CompletionStage<Artifact> downloadArtifact(final Artifact artifact) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    final ArtifactResult artifactResult = this.repositorySystem.resolveArtifact(
                        this.session.getRepositorySession(),
                        new ArtifactRequest(
                            RepositoryUtils.toArtifact(artifact),
                            this.session.getCurrentProject().getRemoteProjectRepositories(),
                            this.getClass().getName()
                        )
                    );
                    artifact.setFile(artifactResult.getArtifact().getFile());
                    artifact.setVersion(artifactResult.getArtifact().getVersion());
                    artifact.setResolved(artifactResult.isResolved());
                    return artifact;
                } catch (final ArtifactResolutionException exc) {
                    throw new RuntimeException(exc);
                }
            }
        );
    }

    /**
     * Will copy the specified artifact into the output directory.
     * @param artifact The artifact already resolved to be copied.
     * @throws MojoFailureException If an error happened while copying the file.
     */
    private void copyFileToDirectory(final Artifact artifact) throws MojoFailureException {
        if (artifact.getFile() == null || !artifact.getFile().exists()
            || !artifact.getFile().isFile()) {
            throw new MojoFailureException(
                String.format("Artifact file not resolved for artifact: %s", artifact.getId())
            );
        }
        try {
            final File outputFile = new File(
                this.outputDirectory,
                Optional.ofNullable(this.outputFileName).orElse(artifact.getFile().getName())
            );
            Files.copy(
                artifact.getFile().toPath(), outputFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (final IOException exc) {
            throw new MojoFailureException(
                String.format("Error copying the file : %s", exc.getMessage())
            );
        }
    }

    /**
     * Unpacks the given artifact file into the output directory.
     * @param artifact The artifact to unpack. The artifact must have an associated file
     *  that exists and is a valid file.
     * @throws NoSuchArchiverException If no suitable unarchiver is found for the artifact file.
     */
    private void unpackFileToDirectory(final Artifact artifact) throws NoSuchArchiverException {
        final File toUnpack = artifact.getFile();
        if (toUnpack != null && toUnpack.exists() && toUnpack.isFile()) {
            final UnArchiver unarchiver = this.archiverManager.getUnArchiver(toUnpack);
            unarchiver.setSourceFile(toUnpack);
            unarchiver.setDestDirectory(this.outputDirectory);
            unarchiver.extract();
        }
    }

    /**
     * Creates the output directory if it does not already exist.
     */
    private void createOutputDirectoryIfNecessary() {
        if (this.outputDirectory != null && !this.outputDirectory.exists()) {
            this.outputDirectory.mkdirs();
        }
    }
}
