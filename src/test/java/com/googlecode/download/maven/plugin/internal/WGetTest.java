package com.googlecode.download.maven.plugin.internal;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.settings.Settings;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.nio.file.Files;

import static java.util.Arrays.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link WGet}
 *
 * @author Andrzej Jarmoniuk
 */
public class WGetTest extends AbstractMojoTestCase {
    private WGet mojo;

    private File cacheDirectory;

    private File outputFile;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        cacheDirectory = Files.createTempDirectory("wget-test-").toFile();
        outputFile = File.createTempFile("wget-test-", "");

        mojo = new WGet();

        BuildContext buildContext = mock(BuildContext.class);
        doNothing().when(buildContext).refresh(any(File.class));

        setVariableValueToObject(mojo, "uri", outputFile.toURI());
        setVariableValueToObject(mojo, "outputDirectory", outputFile.getParentFile());
        setVariableValueToObject(mojo, "cacheDirectory", cacheDirectory);
        setVariableValueToObject(mojo, "retries", 1);
        setVariableValueToObject(mojo, "settings", new Settings());
        setVariableValueToObject(mojo, "buildContext", buildContext);
    }

    private void cleanUp() {
        if (cacheDirectory.exists()) {
            stream(cacheDirectory.listFiles()).forEach(File::delete);
            cacheDirectory.delete();
        }
        if (outputFile.exists()) {
            outputFile.delete();
        }
    }

    @Test
    public void testCacheDirectoryNotCreated()
            throws IllegalAccessException, MojoExecutionException, MojoFailureException {
        setVariableValueToObject(mojo, "skipCache", true);

        try {
            mojo.execute();
            assertThat("Cache directory should remmain empty if skipTest is true", cacheDirectory.list(),
                    Matchers.emptyArray());
        } finally {
            cleanUp();
        }
    }
}
