package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.notify.channel.ActionBarChannel;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player who quits is forgotten by every channel - nobody calls the Notifier for it, the quit event does - and a plugin
 * that leaves stops the channels it registered.
 */
class NotifierQuitTest {

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private TestHud hud;
    private final TestFPlayerSender player = new TestFPlayerSender("Steve");

    @BeforeEach
    void setup() {
        world = Platforms.lenient().install();
        hud = world.platform().getTestHud();
        Notifier.get().registerStock(Plugins.fakeCore(dir.resolve("core").toFile()));
    }

    @AfterEach
    void teardown() {
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    @Test
    void quittingHidesTheBossBarAndEmptiesTheQueues() {
        Notifier.get().notify(player, Notification.of(NotifyKind.TRACKED).id("quest-1").text("Lumberjack").progress(0.3f)
                .channels("boss-bar").build());
        Notifier.get().notify(player, Notification.of(NotifyKind.INFO).text("§9mana").duration(Duration.ofSeconds(30))
                .channels("action-bar").build());
        assertFalse(hud.getBossBarCalls().isEmpty());
        assertNotNull(hud.getBossBar(player, "quest-1"));
        ActionBarChannel actionBar = (ActionBarChannel) Notifier.get().find("action-bar").orElseThrow(AssertionError::new);
        assertEquals(1, actionBar.getQueued(player).size());

        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(player.getUniqueId(), player.getName())));

        TestHud.HudCall last = hud.getBossBarCalls().get(hud.getBossBarCalls().size() - 1);
        assertEquals("hide", last.action);
        assertEquals("quest-1", last.extras.get(0));
        assertNull(hud.getBossBar(player, "quest-1"));
        assertTrue(actionBar.getQueued(player).isEmpty(), "the quit dropped the queue: " + actionBar.getQueued(player));
    }

    @Test
    void aPluginLeavingStopsTheChannelsItRegistered() {
        AtomicInteger cleared = new AtomicInteger();
        NotifyChannel hologram = new NotifyChannel() {
            @Override
            public String getKey() {
                return "finalquests:hologram";
            }

            @Override
            public boolean isSupported() {
                return true;
            }

            @Override
            public void deliver(FPlayer player, Notification notification) {
            }

            @Override
            public void clearAll() {
                cleared.incrementAndGet();
            }
        };
        Notifier.get().register(Plugins.fakePluginData("FinalQuests", dir.resolve("quests").toFile()), hologram, "A floating text.");
        assertTrue(Notifier.get().find("hologram").isPresent());

        Plugins.forget("FinalQuests");

        assertEquals(1, cleared.get(), "its channel kept its players after the plugin left");
        assertFalse(Notifier.get().find("finalquests:hologram").isPresent());
    }
}
