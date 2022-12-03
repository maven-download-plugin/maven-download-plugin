package com.googlecode.download.maven.plugin.test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Auxiliary test utilities
 */
public class TestUtils {

    /**
     * Deletes the given directory together with all its contents
     *
     * @param dir directory to delete
     * @throws IOException should an I/O operation fail
     */
    public static void tearDownTempDir(Path dir) throws IOException {
        if (dir != null && Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return CONTINUE;
                }
            });
        }
    }

    /**
     * Copies the {@code src} directory to {@code dst} recursively,
     * creating the missing directories if necessary
     *
     * @param src source directory path
     * @param dst destination directory path
     * @throws IOException should an I/O error occur
     */
    public static void copyDir(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file)), REPLACE_EXISTING);
                return CONTINUE;
            }
        });
    }

    /**
     * Sets a variable value using reflection
     * @param object object instance with the variable to set
     * @param variable name of the variable
     * @param value value to set
     * @param <T> type of the variable
     */
    public static <T> void setVariableValueToObject(Object object, String variable, T value) {
        try {
            org.apache.maven.plugin.testing.ArtifactStubFactory.setVariableValueToObject(object, variable, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
