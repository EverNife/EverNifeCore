package br.com.finalcraft.evernifecore.testing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every sweep here runs against a base of the test's own: nothing outside it can be reached. */
class TestTempFoldersTest {

    private static final Duration STALE = TestTempFolders.STALE_AFTER.plusHours(1);
    private static final Duration RECENT = TestTempFolders.STALE_AFTER.minusMinutes(1);

    @TempDir
    Path base;

    @TempDirNobodyCleans
    Path fromTheAnnotation;

    @Test
    void aFolderLivesInThisJvmsRootInTheContainerOfTheTempDir() {
        Path folder = TestTempFolders.createFolder("sample-");
        assertTrue(Files.isDirectory(folder));
        assertTrue(folder.getFileName().toString().startsWith("sample-"), folder.toString());
        assertNotEquals(folder, TestTempFolders.createFolder("sample-"));

        Path root = folder.getParent();
        assertTrue(root.getFileName().toString().startsWith("jvm-"), root.toString());
        assertEquals(Paths.get(System.getProperty("java.io.tmpdir")).resolve(TestTempFolders.CONTAINER_NAME), root.getParent());
    }

    @Test
    void theAnnotationCreatesInTheSameRoot() {
        assertEquals(TestTempFolders.createFolder("sample-").getParent(), fromTheAnnotation.getParent());
    }

    @Test
    void aSharedFolderIsTheSameForEveryCaller() throws IOException {
        Path shared = TestTempFolders.getSharedFolder("shared-sample");
        Files.write(shared.resolve("kept.txt"), new byte[]{1});

        assertEquals(shared, TestTempFolders.getSharedFolder("shared-sample"));
        assertTrue(Files.exists(shared.resolve("kept.txt")));
    }

    @Test
    void theNextOpenDeletesAStaleRootAndOnlyThat() throws IOException {
        Path container = base.resolve(TestTempFolders.CONTAINER_NAME);
        Path stale = root(container, "jvm-stale", STALE);
        Path recent = root(container, "jvm-recent", RECENT);
        Path foreign = root(container, "not-a-root", STALE);
        Path besideTheContainer = root(base, "jvm-beside", STALE);
        Path otherFamily = root(base, "junit-other", STALE);
        assertTrue(Files.exists(stale.resolve("deep/file.txt")), "there has to be a stale root to delete");

        Path own = TestTempFolders.openRoot(base);

        assertFalse(Files.exists(stale), "stale root");
        assertTrue(Files.exists(recent.resolve("deep/file.txt")), "a run may still be using a recent root");
        assertTrue(Files.isDirectory(own), "own root");
        assertEquals(container, own.getParent());
        assertTrue(Files.exists(foreign.resolve("deep/file.txt")), "not named as a root, not this engine's");
        assertTrue(Files.exists(besideTheContainer.resolve("deep/file.txt")), "outside the container");
        assertTrue(Files.exists(otherFamily.resolve("deep/file.txt")), "outside the container");
    }

    @Test
    void theOwnRootSurvivesEvenWhenItLooksStale() throws IOException {
        Path container = base.resolve(TestTempFolders.CONTAINER_NAME);
        Path own = root(container, "jvm-own", STALE);

        TestTempFolders.deleteStaleRoots(container, own, Instant.now());

        assertTrue(Files.exists(own.resolve("deep/file.txt")));
    }

    @Test
    void aSweepRefusesAnythingButTheContainerAndDeletesNothing() throws IOException {
        Path stale = root(base, "jvm-stale", STALE);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> TestTempFolders.deleteStaleRoots(base, base.resolve("jvm-own"), Instant.now()));

        assertTrue(refused.getMessage().contains(TestTempFolders.CONTAINER_NAME), refused.getMessage());
        assertTrue(Files.exists(stale.resolve("deep/file.txt")));
    }

    @Test
    void aSymbolicLinkInARootGoesWithoutItsTarget() throws IOException {
        Path target = root(base, "target", RECENT);
        Path root = root(base, "jvm-linked", STALE);
        try {
            Files.createSymbolicLink(root.resolve("link"), target);
        } catch (IOException | UnsupportedOperationException notAllowed) {
            Assumptions.abort("symbolic links need a privilege this machine does not have: " + notAllowed);
        }

        TestTempFolders.deleteTree(root);

        assertFalse(Files.exists(root));
        assertTrue(Files.exists(target.resolve("deep/file.txt")));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aJunctionInARootGoesWithoutItsTarget() throws Exception {
        Path target = root(base, "target", RECENT);
        Path root = root(base, "jvm-linked", STALE);
        Process junction = new ProcessBuilder("cmd", "/c", "mklink", "/J", root.resolve("link").toString(), target.toString())
                .redirectErrorStream(true).redirectOutput(base.resolve("mklink.txt").toFile()).start();
        assertEquals(0, junction.waitFor());
        assertTrue(Files.exists(root.resolve("link/deep/file.txt")), "the junction has to reach the target");

        TestTempFolders.deleteTree(root);

        assertFalse(Files.exists(root));
        assertTrue(Files.exists(target.resolve("deep/file.txt")));
    }

    private static Path root(Path parent, String name, Duration age) throws IOException {
        Path root = Files.createDirectories(parent.resolve(name));
        Files.createDirectories(root.resolve("deep"));
        Files.write(root.resolve("deep/file.txt"), new byte[]{1});
        Files.setLastModifiedTime(root, FileTime.from(Instant.now().minus(age)));
        return root;
    }
}
