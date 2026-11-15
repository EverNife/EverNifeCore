package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.config.plugin.CooldownSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.RepeatPolicy;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Every cooldown has an owner: the same id of two plugins and of the core are three counters, each in its owner's place. */
@ECoreTest
class CooldownOwnerTest {

    @TempDirNobodyCleans
    Path dir;

    private ECPluginData kits;
    private ECPluginData votes;
    private ECPluginData core;

    @BeforeEach
    void setup() {
        core = Plugins.fakeCore(dir.resolve("core").toFile());
        kits = Plugins.fakeOther("FakeKits", dir.resolve("kits").toFile());
        votes = Plugins.fakeOther("FakeVotes", dir.resolve("votes").toFile());
    }

    @AfterEach
    void teardown() {
        Plugins.forget("FakeKits");
        Plugins.forget("FakeVotes");
        Plugins.forget(Plugins.CORE);
        PlayerDataWorld.tearDown();
    }

    @Test
    void theSameServerIdOfTwoPluginsAndOfTheCoreIsThreeCounters() {
        Cooldown.of(kits, "daily").startWith(300);

        assertTrue(Cooldown.of(kits, "daily").isInCooldown());
        assertFalse(Cooldown.of(votes, "daily").isInCooldown(), "another plugin's daily is its own counter");
        assertFalse(Cooldown.of(core, "daily").isInCooldown(), "the core's daily is a third one");
        assertEquals("FakeKits#daily", Cooldown.of(kits, "daily").getIdentifier());
        assertEquals("EverNifeCore#daily", Cooldown.of(core, "daily").getIdentifier());
    }

    @Test
    void theSameNetworkServerIdOfTwoPluginsAndOfTheCoreIsThreeCounters() throws IOException {
        PlayerController.initialize(writeStorageYml("owner_server_network"));

        Cooldown.network(kits, "event").startWith(300);

        assertTrue(Cooldown.network(kits, "event").isInCooldown());
        assertFalse(Cooldown.network(votes, "event").isInCooldown());
        assertFalse(Cooldown.network(core, "event").isInCooldown());
    }

    @Test
    void theSamePlayerIdOfTwoPluginsAndOfTheCoreIsThreeCounters() throws IOException {
        PlayerController.initialize(writeStorageYml("owner_player"));
        UUID uuid = UUID.randomUUID();
        PlayerData player = PlayerController.handleLogin(uuid, "Vip").join();

        player.getCooldown(kits, "daily").join().startWith(300);
        player.getNetworkCooldown(kits, "vote").join().startWith(300);

        assertTrue(player.getCooldown(kits, "daily").join().isInCooldown());
        assertFalse(player.getCooldown(votes, "daily").join().isInCooldown());
        assertFalse(player.getCooldown(core, "daily").join().isInCooldown());
        assertTrue(player.getNetworkCooldown(kits, "vote").join().isInCooldown());
        assertFalse(player.getNetworkCooldown(votes, "vote").join().isInCooldown());
        assertFalse(player.getNetworkCooldown(core, "vote").join().isInCooldown());
    }

    @Test
    void aPersistentServerCooldownIsWrittenInItsOwnersFolderAndNotInTheCores() {
        Cooldown.of(kits, "restart").setPersist(true).startWith(300);

        File kitsFile = new File(kits.getMetaInfo().getDataFolder(), Cooldowns.SERVER_FILE_NAME);
        waitUntilContains(kitsFile, "FakeKits#restart");
        assertFalse(new File(core.getMetaInfo().getDataFolder(), Cooldowns.SERVER_FILE_NAME).exists(),
                "nothing of another plugin is written in the core's file");
    }

    @Test
    void resolveReachesTheSameCounterAsTheFactoryWithoutAPrefixTwice() throws IOException {
        PlayerController.initialize(writeStorageYml("owner_resolve"));
        UUID uuid = UUID.randomUUID();
        PlayerController.handleLogin(uuid, "Vip").join();
        Caller caller = Caller.of(kits, "test");

        Cooldown resolved = Cooldowns.resolve(caller, ProgressOwner.player(uuid), "daily", RepeatPolicy.unlimited());
        assertEquals("FakeKits#daily", resolved.getIdentifier());
        resolved.startWith(300);
        assertTrue(Cooldown.of(kits, uuid, "daily").join().isInCooldown(), "the factory reads the counter resolve started");

        Cooldown global = Cooldowns.resolve(caller, ProgressOwner.global(), "restart", RepeatPolicy.unlimited());
        assertEquals("FakeKits#restart", global.getIdentifier());
        assertSame(Cooldown.of(kits, "restart").getEntry(), global.getEntry());
    }

    @Test
    void aPluginsRetentionOfAnIdDoesNotReachTheSameIdOfAnother() {
        CooldownRetention.setDefaultDays(30);
        CooldownSettings settings = new CooldownSettings();
        settings.setRetentionDays(10);
        settings.getRetentionOverrides().put("daily", 1);
        CooldownRetention.configure(kits, settings);

        assertEquals(TimeUnit.DAYS.toMillis(1), CooldownRetention.retentionMillisFor(Cooldowns.keyOf(kits, "daily")));
        assertEquals(TimeUnit.DAYS.toMillis(10), CooldownRetention.retentionMillisFor(Cooldowns.keyOf(kits, "weekly")));
        assertEquals(TimeUnit.DAYS.toMillis(30), CooldownRetention.retentionMillisFor(Cooldowns.keyOf(votes, "daily")),
                "the other plugin keeps the server's retention");

        Plugins.forget("FakeKits");
        assertEquals(TimeUnit.DAYS.toMillis(30), CooldownRetention.retentionMillisFor("FakeKits#daily"),
                "the retention leaves with its plugin");
    }

    private static void waitUntilContains(File file, String text) {
        long deadline = System.currentTimeMillis() + 3000;
        while (true) {
            try {
                if (file.exists() && new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(text)) {
                    return;
                }
            } catch (IOException stillBeingWritten) {
                //read again
            }
            if (System.currentTimeMillis() > deadline) {
                fail(file + " never held " + text);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
    }

    private File writeStorageYml(String dbName) throws IOException {
        String yml = String.join("\n",
                "storage-backends:",
                "  test_h2:",
                "    enabled: true",
                "    type: h2",
                "    url: \"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1\"",
                "default-backend: test_h2",
                "network:",
                "  storage-backend-id: test_h2",
                "");
        File file = dir.resolve("storage_" + dbName + ".yml").toFile();
        Files.write(file.toPath(), yml.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
