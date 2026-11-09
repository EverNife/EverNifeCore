package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.HeldStateQuitEvent;
import br.com.finalcraft.evernifecore.player.state.HoldOptions;
import br.com.finalcraft.evernifecore.player.state.QuitPolicy;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's own plugin data is global state: a world lends one and hands back whatever was there before. */
class ECoreTestWorldTest {

    @TempDir
    Path tempDir;

    private ECPluginData before;

    @BeforeEach
    void rememberTheCore() {
        before = EverNifeCore.getEcPluginData();
    }

    @AfterEach
    void restoreTheCore() {
        EverNifeCore.instance.onLoaderInstantiate(before);
    }

    @Test
    void aPlayerOnAReconnectionWindowDoesNotOutliveTheWorld() {
        StateHolder duel = StateHolder.of("theduel:battle");
        TestFPlayerSender player = new TestFPlayerSender("duelist");
        List<HeldStateQuitEvent.Expired> expired = new CopyOnWriteArrayList<>();
        TestClock.acquire();
        try {
            ECoreTestWorld world = Platforms.lenient().playerState().install();
            PlayerDataWorld playerData = PlayerDataWorld.with(Storages.memory()).boot(tempDir);
            try {
                PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
                HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, duel, "kit", p -> { },
                        HoldOptions.persistent().onQuit(QuitPolicy.keepFor(Duration.ofSeconds(10))), player);
                HeldPlayerState.forget(player.getUniqueId(), player);
                assertTrue(HeldPlayerState.of(player.getUniqueId()).getHolders(HeldPlayerState.INVENTORY).containsKey(duel.getHolderId()), "on the window");
            } finally {
                playerData.close();
                world.close();
            }

            ECoreTestWorld next = Platforms.lenient().playerState().install();
            ECEventSubscription<HeldStateQuitEvent.Expired> listening = ECEventBus.global().subscribe(HeldStateQuitEvent.Expired.class, expired::add);
            try {
                assertTrue(HeldPlayerState.of(player.getUniqueId()).getHolders(HeldPlayerState.INVENTORY).isEmpty(), "no state for the next world");
                HeldPlayerState.forget(player.getUniqueId(), null);
                TestClock.advance(Duration.ofSeconds(11));
                TestClock.runDue();
                assertTrue(expired.isEmpty(), "and no task of the old window does anything");
            } finally {
                listening.unsubscribe();
                next.close();
            }
        } finally {
            TestClock.release();
        }
    }

    @Test
    void worldsWithoutACoreReuseOneCoreFolder() {
        EverNifeCore.instance.onLoaderInstantiate(null);
        File first;
        try (ECoreTestWorld world = Platforms.lenient().install()) {
            first = EverNifeCore.getEcPluginData().getMetaInfo().getDataFolder();
        }
        assertNull(EverNifeCore.getEcPluginData(), "the world took its core out");

        try (ECoreTestWorld world = Platforms.lenient().install()) {
            assertEquals(first, EverNifeCore.getEcPluginData().getMetaInfo().getDataFolder());
        }
    }

    @Test
    void closeHandsBackTheCorePluginData() {
        EverNifeCore.instance.onLoaderInstantiate(null);

        ECoreTestWorld world = Platforms.lenient().install();
        assertNotNull(EverNifeCore.getEcPluginData(), "a world gives the core a plugin data when nobody did");
        world.close();

        assertNull(EverNifeCore.getEcPluginData(), "the core had none before the world, and has none after it");
    }

    @Test
    void aPluginDataLentToTheCoreGoesBackOnClose() {
        try (ECoreTestWorld outer = Platforms.lenient().install()) {
            ECPluginData original = Plugins.fakeCore(tempDir.resolve("original").toFile());
            Plugins.forget(Plugins.CORE);
            outer.withCorePluginData(original);

            ECoreTestWorld world = Platforms.lenient().install();
            ECPluginData lent = Plugins.fakePluginData("LentCore", tempDir.resolve("lent").toFile());
            world.withCorePluginData(lent);
            assertSame(lent, EverNifeCore.getEcPluginData());
            Plugins.forget("LentCore");
            world.close();

            assertSame(original, EverNifeCore.getEcPluginData(), "the inner world hands back the outer world's core");
        }
    }
}
