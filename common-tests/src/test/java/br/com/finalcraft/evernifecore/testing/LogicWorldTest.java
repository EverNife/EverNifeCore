package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.group.CoreGroups;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A LogicWorld opened beside a GroupWorld and a core the test built: it shares them instead of replacing them. */
@ECoreTest
class LogicWorldTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void aLogicWorldBesideAGroupWorldKeepsTheTestsCoreAndTheOtherWorldsPlayers() {
        ECoreTestWorld world = Platforms.lenient().mainThreadInline().install();
        try {
            ECPluginData core = Plugins.fakeCore(tempDir.resolve("core").toFile());
            CoreGroups.registerAll(core);
            try (GroupWorld parties = GroupWorld.open("FakeParty", tempDir.resolve("party"))) {
                TestFPlayerSender steve = parties.player("Steve");
                try (LogicWorld quests = LogicWorld.open("FakeQuests", tempDir.resolve("quests"))) {
                    TestFPlayerSender alex = quests.player("Alex");

                    assertSame(core, ECPluginManager.getECPluginsMap().get(Plugins.CORE), "the stock goes on the core the test built");
                    assertTrue(Conditions.registry().find("evernifecore:all").isPresent());
                    assertTrue(EverNifeCore.getPlatform().getOnlinePlayers().contains(steve), "the group world's player stays online");
                    assertTrue(EverNifeCore.getPlatform().getOnlinePlayers().contains(alex));
                    assertEquals(parties.now(), quests.now(), "one clock");
                }

                assertSame(core, ECPluginManager.getECPluginsMap().get(Plugins.CORE), "closing the logic world leaves the test's core");
                assertTrue(ProgressOwners.find(ProgressOwner.GROUP).isPresent(), "and what the test registered on it");
                assertTrue(EverNifeCore.getPlatform().getOnlinePlayers().contains(steve), "it takes only its own players off");
                assertFalse(EverNifeCore.getPlatform().getOnlinePlayers().stream().anyMatch(p -> p.getName().equals("Alex")));
            } finally {
                Plugins.forget(Plugins.CORE);
            }
        } finally {
            world.close();
        }
    }
}
