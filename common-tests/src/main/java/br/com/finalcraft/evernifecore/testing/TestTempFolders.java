package br.com.finalcraft.evernifecore.testing;

import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;

/**
 * The folders of a test run: everything lives under one root per JVM,
 * {@code <java.io.tmpdir>/evernifecore-tests/jvm-<random>}, created by the first folder asked for.
 *
 * <p>The JVM deletes its own root when it exits, as far as it can: a file an asynchronous writer still holds on Windows
 * stays. Whatever stayed goes when a later JVM opens its root, which deletes the sibling roots older than
 * {@link #STALE_AFTER}. A build needs no setting for any of this.</p>
 */
public final class TestTempFolders {

    static final String CONTAINER_NAME = "evernifecore-tests";

    private static final String ROOT_PREFIX = "jvm-";

    // reason-detre: the age is the root's own modified time, so a run that adds no folder for this long loses its root to the next JVM; raise it if a test task may ever run that long
    static final Duration STALE_AFTER = Duration.ofHours(1);

    private static Path ownRoot;

    private TestTempFolders() {
    }

    /** A new, empty folder inside this JVM's root, named {@code prefix} plus a random part. */
    public static Path createFolder(String prefix) {
        try {
            return Files.createTempDirectory(ownRoot(), prefix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The one folder called {@code name} in this JVM's root: every caller gets the same one, with what earlier callers left in it. */
    public static Path getSharedFolder(String name) {
        try {
            return Files.createDirectories(ownRoot().resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static synchronized Path ownRoot() throws IOException {
        if (ownRoot == null) {
            Path root = openRoot(Paths.get(System.getProperty("java.io.tmpdir")));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(root), "ecore-test-temp-folders"));
            ownRoot = root;
        }
        return ownRoot;
    }

    /** Creates a new root under {@code base}'s container and clears the stale ones beside it. */
    static Path openRoot(Path base) throws IOException {
        Path container = Files.createDirectories(base.resolve(CONTAINER_NAME));
        Path root = Files.createTempDirectory(container, ROOT_PREFIX);
        deleteStaleRoots(container, root, Instant.now());
        return root;
    }

    /**
     * Deletes the roots of {@code container} older than {@link #STALE_AFTER} at {@code now}, never {@code own}. Only an entry
     * that is a root goes, and one this JVM cannot delete stays for the next.
     *
     * @throws IllegalArgumentException when {@code container} is not named {@value #CONTAINER_NAME}, before touching anything
     */
    static void deleteStaleRoots(Path container, Path own, Instant now) {
        if (!CONTAINER_NAME.equals(String.valueOf(container.getFileName()))) {
            throw new IllegalArgumentException("Refusing to delete anything in " + container + ": test roots live only in a folder named "
                    + CONTAINER_NAME + ". Pass <java.io.tmpdir>/" + CONTAINER_NAME + ", never the temp dir itself.");
        }
        Instant limit = now.minus(STALE_AFTER);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(container, ROOT_PREFIX + "*")) {
            for (Path entry : entries) {
                if (!entry.equals(own) && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) && isOlderThan(entry, limit)) {
                    deleteTree(entry);
                }
            }
        } catch (IOException | DirectoryIteratorException ignored) {
            //another JVM is sweeping too, or the folder is held: a failed sweep must not fail the test that asked for a folder
        }
    }

    private static boolean isOlderThan(Path root, Instant limit) {
        try {
            return Files.getLastModifiedTime(root).toInstant().isBefore(limit);
        } catch (IOException gone) {
            return false; //another JVM just deleted it
        }
    }

    /** Deletes {@code root} and what is in it, as far as it can. A link goes without what it points at. */
    static void deleteTree(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (attrs.isOther()) { //a Windows junction reads as a directory, and walking in would empty its target
                        deleteQuietly(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    deleteQuietly(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure) {
                    deleteQuietly(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            //the visitor swallows every failure; what stays is the next JVM's to delete
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            //held by a writer on Windows, or a directory with such a file inside
        }
    }

    /** Hands JUnit a folder inside this JVM's root, for {@link TempDirNobodyCleans}. */
    public static final class Factory implements TempDirFactory {

        @Override
        public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext) {
            return createFolder("junit-");
        }
    }
}
