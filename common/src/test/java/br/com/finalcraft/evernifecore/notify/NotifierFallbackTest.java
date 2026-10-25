package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A kind's default chain on a platform that lacks its first channels: the next step whose channels are all there. */
class NotifierFallbackTest {

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private TestHud hud;
    private final TestFPlayerSender player = new TestFPlayerSender("Steve");

    private void platform(String id, IHudAccess.Capability... missing) {
        world = Platforms.lenient().hudWithout(missing).platformProviderId(id).install();
        hud = world.platform().getTestHud();
        Notifier.get().registerStock(Plugins.fakeCore(dir.resolve("core").toFile()));
    }

    @AfterEach
    void teardown() {
        if (world != null) {
            Plugins.forget(Plugins.CORE);
            world.close();
        }
    }

    /** 1.7.10 without NecroTempus has no title: COMPLETE's {@code title+sound} gives way to {@code chat+sound}. */
    @Test
    void completeWithoutATitleFallsToChatAndSound() {
        platform("minecraft", IHudAccess.Capability.TITLE);

        Notifier.get().notify(player, Notification.of(NotifyKind.COMPLETE).text("§aquest done").sound(HudSounds.LEVEL_UP).build());

        assertEquals(1, hud.getSoundCalls().size(), "the sound of chat+sound: " + hud.getSoundCalls());
        assertEquals(HudSounds.LEVEL_UP, hud.getSoundCalls().get(0).text);
        assertEquals(1, player.getMessages().size(), "the chat of chat+sound: " + player.getMessages());
        assertTrue(hud.getTitleCalls().isEmpty(), "no title on a platform without one");
    }

    /** Hytale has no boss bar and no action bar: TRACKED lands on its quest tracker. */
    @Test
    void trackedWithoutABossBarLandsOnTheTracker() {
        platform("hytale", IHudAccess.Capability.BOSS_BAR, IHudAccess.Capability.ACTION_BAR,
                IHudAccess.Capability.SIDEBAR, IHudAccess.Capability.COMPASS);

        Notifier.get().notify(player, Notification.of(NotifyKind.TRACKED).id("quest-1").title("Lumberjack")
                .trackerLines(Collections.singletonList(TrackerLine.of(FancyText.of("logs"), 3, 10)))
                .build());

        assertEquals(1, hud.getTrackerCalls().size(), "tracker: " + hud.getTrackerCalls());
        assertEquals("show", hud.getTrackerCalls().get(0).action);
        assertTrue(hud.getBossBarCalls().isEmpty());
        assertTrue(player.getMessages().isEmpty(), "the tracker step was taken, so chat was not: " + player.getMessages());
    }

    @Test
    void aChainWithNoSupportedStepDeliversNothingThrowsNothingAndLogsOnce() {
        platform("fallback-test", IHudAccess.Capability.TOAST, IHudAccess.Capability.BOSS_BAR);
        Notification notification = Notification.of(NotifyKind.INFO).text("§eunseen").channels("toast", "boss-bar").build();

        List<String> logged = Logs.capture(() -> {
            Notifier.get().notify(player, notification);
            Notifier.get().notify(player, notification);
        });

        assertTrue(player.getMessages().isEmpty());
        assertTrue(hud.getToastCalls().isEmpty() && hud.getBossBarCalls().isEmpty());
        long warnings = logged.stream().filter(line -> line.contains("[toast, boss-bar]")).count();
        assertEquals(1, warnings, "one warning for two notifications: " + logged);
        assertTrue(logged.stream().anyMatch(line -> line.contains("(fallback-test)") && line.contains("chat")), logged.toString());
    }
}
