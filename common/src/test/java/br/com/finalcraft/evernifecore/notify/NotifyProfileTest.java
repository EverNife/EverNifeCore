package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.config.plugin.NotifySettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each plugin's notifications: ids scoped to the plugin, and the chains and durations of the profile it declares. */
class NotifyProfileTest {

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private TestHud hud;
    private ECPluginData quests;
    private ECPluginData jobs;
    private final TestFPlayerSender player = new TestFPlayerSender("Steve");

    @BeforeEach
    void setup() {
        world = Platforms.lenient().install();
        hud = world.platform().getTestHud();
        Notifier.get().registerStock(Plugins.fakeCore(dir.resolve("core").toFile()));
        quests = Plugins.fakeOther("FakeQuests", dir.resolve("quests").toFile());
        jobs = Plugins.fakeOther("FakeJobs", dir.resolve("jobs").toFile());
        TestClock.acquire();
    }

    @AfterEach
    void teardown() {
        Plugins.forget("FakeQuests");
        Plugins.forget("FakeJobs");
        Plugins.forget(Plugins.CORE);
        world.close();
        TestClock.release();
    }

    private static NotifySettings settings(NotifyKind kind, String... chain) {
        NotifySettings settings = new NotifySettings();
        settings.getChains().put(kind.getKey(), Arrays.asList(chain));
        return settings;
    }

    @Test
    void twoPluginsWithTheSameIdShowTwoBossBarsAndClearingOneLeavesTheOther() {
        Notifier.get().notify(player, Notification.of(quests, NotifyKind.TRACKED).id("progress").text("§eLogs").channels("boss-bar").build());
        Notifier.get().notify(player, Notification.of(jobs, NotifyKind.TRACKED).id("progress").text("§eOres").channels("boss-bar").build());

        assertNotNull(hud.getBossBar(player, "fakequests:progress"));
        assertNotNull(hud.getBossBar(player, "fakejobs:progress"));

        Notifier.get().clear(player, quests, "progress");

        assertNull(hud.getBossBar(player, "fakequests:progress"));
        assertNotNull(hud.getBossBar(player, "fakejobs:progress"), "the other plugin's bar went with the clear");
    }

    @Test
    void twoPluginsWithDifferentProfilesDeliverProgressThroughDifferentChannels() {
        NotifyProfile.register(quests, settings(NotifyKind.PROGRESS, "boss-bar"));
        NotifyProfile.register(jobs, settings(NotifyKind.PROGRESS, "tracker"));

        Notifier.get().notify(player, Notification.of(quests, NotifyKind.PROGRESS).id("logs").text("§aLogs").build());
        Notifier.get().notify(player, Notification.of(jobs, NotifyKind.PROGRESS).id("ores").text("§aOres").build());

        assertNotNull(hud.getBossBar(player, "fakequests:logs"));
        assertNull(hud.getBossBar(player, "fakejobs:ores"));
        assertEquals(1, hud.getTrackerCalls().size(), "the jobs plugin's progress goes to the tracker: " + hud.getTrackerCalls());
        assertEquals("fakejobs:ores", hud.getTrackerCalls().get(0).extras.get(0));
    }

    @Test
    void aPluginWithoutAProfileUsesTheBuiltinDefault() {
        assertEquals(NotifyChain.parse("title+sound", "chat+sound", "chat"), Notifier.get().getChain(quests, NotifyKind.COMPLETE));
        assertEquals(NotifyChain.parse("boss-bar", "tracker", "action-bar", "chat"), Notifier.get().getChain(quests, NotifyKind.TRACKED));
        assertEquals(Duration.ofSeconds(3), Notification.of(quests, NotifyKind.PROGRESS).build().getDuration());
        assertEquals(Duration.ZERO, Notification.of(quests, NotifyKind.TRACKED).build().getDuration(), "a tracked one stays until cleared");
    }

    @Test
    void aChainNamingAnUnknownChannelFailsTheRegisterNamingTheKeyAndTheChannels() {
        NotifyException refused = assertThrows(NotifyException.class,
                () -> NotifyProfile.register(quests, settings(NotifyKind.PROGRESS, "acton-bar")));

        String message = refused.getMessage();
        assertTrue(message.contains("FakeQuests"), message);
        assertTrue(message.contains("notify.chains.progress"), message);
        assertTrue(message.contains("acton-bar"), message);
        assertTrue(message.contains("[chat, action-bar, title, sound, boss-bar, sidebar, toast, tracker]"), message);
        assertEquals(NotifyChain.parse("action-bar", "chat"), Notifier.get().getChain(quests, NotifyKind.PROGRESS),
                "a refused profile changes nothing");
    }

    @Test
    void aKeyThatIsNoKindFailsTheRegisterNamingTheKinds() {
        NotifySettings settings = new NotifySettings();
        settings.getDurations().put("progres", FCTimeFrame.of(Duration.ofSeconds(1)));

        NotifyException refused = assertThrows(NotifyException.class, () -> NotifyProfile.register(quests, settings));

        assertTrue(refused.getMessage().contains("notify.durations.progres"), refused.getMessage());
        assertTrue(refused.getMessage().contains("[progress, complete, tracked, info, deny]"), refused.getMessage());
    }

    @Test
    void aPluginChannelIsAcceptedBeforeThePluginRegistersIt() {
        NotifyProfile.register(quests, settings(NotifyKind.DENY, "finalquests:hologram", "chat"));

        assertEquals(NotifyChain.parse("finalquests:hologram", "chat"), Notifier.get().getChain(quests, NotifyKind.DENY));
    }

    @Test
    void registeringAgainReplacesTheProfileAndLeavesWhatIsOnScreen() {
        NotifyProfile.register(quests, settings(NotifyKind.TRACKED, "boss-bar"));
        Notifier.get().notify(player, Notification.of(quests, NotifyKind.TRACKED).id("quest").text("§eLogs").build());

        NotifySettings reloaded = settings(NotifyKind.TRACKED, "tracker");
        reloaded.getDurations().put(NotifyKind.TRACKED.getKey(), FCTimeFrame.of(Duration.ofSeconds(1)));
        NotifyProfile.register(quests, reloaded);

        assertEquals(NotifyChain.parse("tracker"), Notifier.get().getChain(quests, NotifyKind.TRACKED));
        TestClock.advance(Duration.ofSeconds(2));
        TestClock.runDue();
        assertNotNull(hud.getBossBar(player, "fakequests:quest"), "the bar on screen keeps the duration it was shown with");

        Notifier.get().notify(player, Notification.of(quests, NotifyKind.TRACKED).id("next").text("§eOres").build());
        assertEquals(1, hud.getTrackerCalls().size(), "the next one takes the new chain: " + hud.getTrackerCalls());
    }

    @Test
    void theProfileLeavesWithThePlugin() {
        NotifyProfile.register(quests, settings(NotifyKind.PROGRESS, "chat"));
        assertEquals(NotifyChain.parse("chat"), Notifier.get().getChain(quests, NotifyKind.PROGRESS));

        ECPluginManager.removePluginData("FakeQuests");

        assertEquals(NotifyChain.parse("action-bar", "chat"), Notifier.get().getChain(quests, NotifyKind.PROGRESS),
                "the removed plugin's profile is still registered");
    }
}
