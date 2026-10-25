package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.notify.BossBarColor;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.notify.ToastStyle;
import br.com.finalcraft.evernifecore.notify.TrackerLine;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The title, boss bar, tracker, sidebar and toast channels: what each puts on the screen, and when it takes it off. */
class HudChannelsTest {

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
        TestClock.acquire();
    }

    @AfterEach
    void teardown() {
        Plugins.forget(Plugins.CORE);
        world.close();
        TestClock.release();
    }

    @Test
    void aTitleIsSentOnceWithItsTimesAndClearedWhenItEnds() {
        Notifier.get().notify(player, Notification.of(NotifyKind.COMPLETE).title("§aDone").subtitle("§7well played")
                .duration(Duration.ofSeconds(2)).channels("title").build());
        TestClock.advance(Duration.ofMillis(QueuedHudChannel.TICK_MILLIS));
        TestClock.runDue();

        assertEquals(1, hud.getTitleCalls().size(), "a title sent again restarts its fade: " + hud.getTitleCalls());
        TestHud.HudCall sent = hud.getTitleCalls().get(0);
        assertEquals("§aDone", sent.text);
        assertEquals(Arrays.asList("§7well played", TitleChannel.FADE_IN_TICKS, 40, TitleChannel.FADE_OUT_TICKS), sent.extras);

        TestClock.advance(Duration.ofSeconds(3)); //fade in + 2 s + fade out = 3.5 s from the start
        TestClock.runDue();
        assertEquals("clear", hud.getTitleCalls().get(hud.getTitleCalls().size() - 1).action);
    }

    @Test
    void aBossBarShowsItsProgressAndUpdatesInPlace() {
        Notifier.get().notify(player, Notification.of(NotifyKind.TRACKED).id("quest").text("§eLogs").progress(0.25f).channels("boss-bar").build());
        Notifier.get().notify(player, Notification.of(NotifyKind.TRACKED).id("quest").text("§eLogs").progress(0.5f).channels("boss-bar").build());

        TestHud.HudCall shown = hud.getBossBar(player, "quest");
        assertNotNull(shown);
        assertEquals(Arrays.<Object>asList("quest", 0.5f, BossBarColor.PURPLE), shown.extras);
    }

    @Test
    void aBossBarWithADurationHidesWhenItRunsOutButNotWhenItWasShownAgain() {
        Notifier.get().notify(player, Notification.of(NotifyKind.PROGRESS).id("xp").text("§a+10").duration(Duration.ofSeconds(3)).channels("boss-bar").build());
        TestClock.advance(Duration.ofSeconds(2));
        Notifier.get().notify(player, Notification.of(NotifyKind.PROGRESS).id("xp").text("§a+20").duration(Duration.ofSeconds(3)).channels("boss-bar").build());

        TestClock.advance(Duration.ofSeconds(1));
        TestClock.runDue();
        assertNotNull(hud.getBossBar(player, "xp"), "the first timer belongs to a bar that was shown again since");

        TestClock.advance(Duration.ofSeconds(2));
        TestClock.runDue();
        assertNull(hud.getBossBar(player, "xp"), "the second one runs out three seconds after it was shown");
    }

    @Test
    void theTrackerShowsCountedLinesAndHidesOnClear() {
        List<TrackerLine> lines = Arrays.asList(TrackerLine.of(FancyText.of("logs"), 3, 10), TrackerLine.of(FancyText.of("return")));
        Notifier.get().notify(player, Notification.of(NotifyKind.TRACKED).id("quest").title("Lumberjack").trackerLines(lines).channels("tracker").build());

        Notifier.get().clear(player, "quest");

        assertEquals(2, hud.getTrackerCalls().size());
        assertEquals(Arrays.<Object>asList("quest", lines), hud.getTrackerCalls().get(0).extras);
        assertEquals("Lumberjack", hud.getTrackerCalls().get(0).text);
        assertEquals("hide", hud.getTrackerCalls().get(1).action);
    }

    @Test
    void theSidebarHasOneSlotAndTheToastTakesItsStyleFromTheKind() {
        Notifier.get().notify(player, Notification.of(NotifyKind.INFO).id("a").title("Stats").lines(Arrays.asList(FancyText.of("kills: 3"))).channels("sidebar").build());
        Notifier.get().notify(player, Notification.of(NotifyKind.INFO).id("b").title("Other").channels("sidebar").build());
        Notifier.get().clear(player, "a");
        assertEquals(2, hud.getSidebarCalls().size(), "clearing an id that lost the slot leaves the sidebar alone: " + hud.getSidebarCalls());

        Notifier.get().notify(player, Notification.of(NotifyKind.DENY).text("§cno").channels("toast").build());
        assertEquals(Arrays.<Object>asList("", ToastStyle.DANGER), hud.getToastCalls().get(0).extras);
    }
}
