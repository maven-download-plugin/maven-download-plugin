package io.github.download.maven.plugin.internal;

import java.io.File;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class FilePermissionsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void applyExecutable() throws IOException, MojoExecutionException {
        final File outputFile = this.temporaryFolder.newFile();
        new FilePermissions("+x", Mockito.mock(Log.class)).applyTo(outputFile);
        MatcherAssert.assertThat(
            outputFile.canExecute(),
            Matchers.is(true)
        );
    }

    @Test
    public void applyExecutableAgain() throws IOException, MojoExecutionException {
        final File outputFile = this.temporaryFolder.newFile();
        outputFile.setExecutable(true);
        new FilePermissions("+x", Mockito.mock(Log.class)).applyTo(outputFile);
        MatcherAssert.assertThat(
            outputFile.canExecute(),
            Matchers.is(true)
        );
    }

    @Test
    public void failOnUnknownCommand() throws IOException {
        final File outputFile = this.temporaryFolder.newFile();
        outputFile.setExecutable(true);
        Assert.assertThrows(
            MojoExecutionException.class,
            () -> new FilePermissions("-x", Mockito.mock(Log.class)).applyTo(outputFile)
        );
    }
}
