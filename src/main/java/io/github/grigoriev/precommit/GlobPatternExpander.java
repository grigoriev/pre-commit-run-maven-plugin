package io.github.grigoriev.precommit;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Expands glob patterns to matching files.
 */
public class GlobPatternExpander {

    private final Consumer<String> warningLogger;

    /**
     * Creates a GlobPatternExpander with no-op warning logger.
     */
    public GlobPatternExpander() {
        this(message -> { });
    }

    /**
     * Creates a GlobPatternExpander with the specified warning logger.
     *
     * @param warningLogger consumer to receive warning messages
     */
    public GlobPatternExpander(Consumer<String> warningLogger) {
        this.warningLogger = warningLogger;
    }

    /**
     * Checks if the given path contains glob pattern characters.
     *
     * @param path the path to check
     * @return true if the path contains glob characters
     */
    public boolean isGlobPattern(String path) {
        return path.contains("*") || path.contains("?") || path.contains("[");
    }

    /**
     * Expands a glob pattern to matching files.
     *
     * @param pattern the glob pattern
     * @param baseDir the base directory to search in
     * @return list of matching files
     */
    public List<File> expand(String pattern, File baseDir) {
        List<File> matchedFiles = new ArrayList<>();
        Path startDir = baseDir.toPath();

        String globPattern = "glob:" + pattern;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

        try {
            walkFileTree(startDir, matcher, matchedFiles);
        } catch (IOException e) {
            warningLogger.accept("Failed to expand glob pattern: " + pattern + " - " + e.getMessage());
        }

        return matchedFiles;
    }

    /**
     * Walks the file tree and collects matching files.
     * Protected for testing purposes.
     *
     * @param startDir the directory to start walking from
     * @param matcher the path matcher to use for filtering files
     * @param matchedFiles list to collect matching files into
     * @throws IOException if an I/O error occurs while walking the file tree
     */
    protected void walkFileTree(Path startDir, PathMatcher matcher, List<File> matchedFiles) throws IOException {
        Files.walkFileTree(startDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relativePath = startDir.relativize(file);
                if (matcher.matches(relativePath)) {
                    matchedFiles.add(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
