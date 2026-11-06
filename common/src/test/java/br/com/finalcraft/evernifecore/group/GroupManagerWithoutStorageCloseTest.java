package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A manager with nothing to write closes at once, even on a test clock nobody pumps. */
@ECoreTest
class GroupManagerWithoutStorageCloseTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void closingAManagerWithoutStorageDoesNotWaitForTheScheduler() {
        try (ECoreTestWorld world = Platforms.lenient().install();
             RegionWorld regions = RegionWorld.open("FakeArena", tempDir.resolve("arena"))) {
            ECPluginData duel = Plugins.fakeOther("FakeDuel", tempDir.resolve("duel").toFile());
            GroupManager teams = GroupManager.of(duel);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> Plugins.forget("FakeDuel"),
                    "the owner's shutdown waited for a write that has nowhere to go");
            assertTrue(teams.isClosed());
            assertNull(GroupManager.ofIfPresent(duel));
        }
    }

    @Test
    void theOwnersShutdownClosesAManagerWithoutStorageAtOnce() {
        try (ECoreTestWorld world = Platforms.lenient().install();
             RegionWorld regions = RegionWorld.open("FakeArena", tempDir.resolve("arena2"))) {
            ECPluginData duel = Plugins.fakeOther("FakeDuel2", tempDir.resolve("duel2").toFile());
            GroupManager teams = GroupManager.of(duel);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> GroupManager.closeOwnedBy(duel),
                    "the owner's shutdown waited for a write that has nowhere to go");
            assertTrue(teams.isClosed());
            Plugins.forget("FakeDuel2");
        }
    }

    @Test
    void closeCompletesAtOnceWithoutStorage() {
        try (ECoreTestWorld world = Platforms.lenient().install();
             RegionWorld regions = RegionWorld.open("FakeArena", tempDir.resolve("arena3"))) {
            ECPluginData duel = Plugins.fakeOther("FakeDuel3", tempDir.resolve("duel3").toFile());

            assertTrue(GroupManager.of(duel).close().isDone(), "nothing to write, so nothing to wait for on the scheduler");
            Plugins.forget("FakeDuel3");
        }
    }
}
