package br.com.finalcraft.evernifecore.notify.channel;

import br.com.finalcraft.evernifecore.notify.IHudAccess;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What lands on a player's action bar, and what never does. The queue's tick runs on the {@link TestClock} here, so a
 * tick happens exactly when a test calls {@link TestClock#runDue()}; the {@link Isolation} cases run it on the real
 * scheduler, since what they pin is a tick that outlives its test.
 */
class ActionBarChannelTest {

    private static final Duration TICK = Duration.ofMillis(QueuedHudChannel.TICK_MILLIS);

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private TestHud hud;
    private final TestFPlayerSender player = new TestFPlayerSender("Steve");
    private boolean clockHeld;

    private void serverWithActionBar(boolean supported, boolean testClock) {
        world = (supported ? Platforms.lenient() : Platforms.lenient().hudWithout(IHudAccess.Capability.ACTION_BAR)).install();
        hud = world.platform().getTestHud();
        Notifier.get().registerStock(Plugins.fakeCore(dir.resolve("core").toFile()));
        if (testClock) {
            TestClock.acquire();
            clockHeld = true;
        }
    }

    @AfterEach
    void teardown() {
        if (world != null) {
            Plugins.forget(Plugins.CORE);
            world.close();
        }
        if (clockHeld) {
            TestClock.release();
        }
    }

    private static Notification bar(String id, String text, int priority, Duration lasting) {
        return Notification.of(NotifyKind.INFO).id(id).text(text).priority(priority).duration(lasting).channels("action-bar").build();
    }

    private static Notification bar(String id, String text, int priority) {
        return bar(id, text, priority, Duration.ofSeconds(30));
    }

    private List<String> textsOnScreen(TestHud screen) {
        List<String> texts = new ArrayList<>();
        for (TestHud.HudCall call : screen.getActionBarCalls()) {
            texts.add(call.text);
        }
        return texts;
    }

    private String lastTextOnScreen() {
        List<String> texts = textsOnScreen(hud);
        assertFalse(texts.isEmpty(), "nothing was ever put on the action bar");
        return texts.get(texts.size() - 1);
    }

    private ActionBarChannel channel() {
        return (ActionBarChannel) Notifier.get().find("action-bar").orElseThrow(AssertionError::new);
    }

    @Test
    void aServerWithoutAnActionBarIsNeverSentOneAndTheChainFallsToChat() {
        serverWithActionBar(false, true);

        Notifier.get().notify(player, Notification.of(NotifyKind.INFO).text("§ewelcome").channels("action-bar", "chat").build());

        assertTrue(hud.getActionBarCalls().isEmpty(), "a server that cannot show an action bar must not be handed one");
        assertEquals(1, player.getMessages().size(), "the next step of the chain, chat, took it: " + player.getMessages());
    }

    @Test
    void aServerWithAnActionBarGetsTheTextAndKeepsWritingItEveryTick() {
        serverWithActionBar(true, true);

        Notifier.get().notify(player, bar("greeting", "§ewelcome", 0));
        TestClock.advance(TICK);
        TestClock.runDue();

        assertEquals(2, hud.getActionBarCalls().size(), "once on delivery, once on the tick: " + textsOnScreen(hud));
        assertEquals("§ewelcome", lastTextOnScreen());
    }

    /** Two messages at once is the normal case - the player has one bar and the loudest claim wins it. */
    @Test
    void theHighestPriorityMessageIsTheOneOnScreen() {
        serverWithActionBar(true, true);

        Notifier.get().notify(player, bar("combat", "§cunder attack", 10));
        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0));

        assertEquals("§cunder attack", lastTextOnScreen(), "the low-priority bar is still queued, but it is not the one shown");
        assertEquals(2, channel().getQueued(player).size());
    }

    @Test
    void theSameIdReplacesTheQueuedMessage() {
        serverWithActionBar(true, true);

        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0));
        Notifier.get().notify(player, bar("mana", "§9mana 39/40", 0));

        assertEquals("§9mana 39/40", lastTextOnScreen());
        assertEquals(1, channel().getQueued(player).size(), "one id is one entry: " + channel().getQueued(player));
    }

    @Test
    void anExpiredMessageLeavesAndTheNextOneTakesTheBar() {
        serverWithActionBar(true, true);
        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0, Duration.ofSeconds(30)));
        Notifier.get().notify(player, bar("combat", "§cunder attack", 10, Duration.ofSeconds(3)));

        TestClock.advance(Duration.ofMillis(2500));
        TestClock.runDue();
        assertEquals("§cunder attack", lastTextOnScreen(), "two and a half seconds into three, it is still on");

        TestClock.advance(Duration.ofMillis(500));
        TestClock.runDue();
        assertEquals("§9mana 40/40", lastTextOnScreen(), "three seconds is three seconds: then the queued one shows");
    }

    /** Nothing tells the queue the player left; it is the tick itself that has to notice. */
    @Test
    void aPlayerWhoLeftIsSentNothingMore() {
        serverWithActionBar(true, true);
        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0));

        player.online(false);
        Notifier.get().notify(player, bar("combat", "§cunder attack", 10));
        TestClock.advance(TICK);
        TestClock.runDue();

        assertFalse(textsOnScreen(hud).contains("§cunder attack"), "a player who is gone has no screen: " + textsOnScreen(hud));
        assertTrue(textsOnScreen(hud).contains("§9mana 40/40"));
    }

    @Test
    void clearingTheOnlyMessagePutsAnEmptyBarOnScreen() {
        serverWithActionBar(true, true);
        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0));

        Notifier.get().clear(player, "mana");

        assertEquals("", lastTextOnScreen(), "an action bar goes away by being overwritten with nothing");
        assertTrue(channel().getQueued(player).isEmpty());
    }

    @Test
    void ticksAndSecondsAgreeOnHowLongTheyAre() {
        Duration bySeconds = Notification.of(NotifyKind.INFO).duration(Duration.ofSeconds(1)).build().getDuration();
        Duration byTicks = Notification.of(NotifyKind.INFO).ticks(20).build().getDuration();

        assertEquals(bySeconds, byTicks, "one second and twenty ticks are the same duration");
    }

    @Test
    void closingTheWorldStopsTheTickBeforeItReturns() throws InterruptedException {
        serverWithActionBar(true, false);
        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0));
        world.close();
        int writtenWhenClosed = hud.getActionBarCalls().size();

        Thread.sleep(3 * QueuedHudChannel.TICK_MILLIS);

        assertEquals(writtenWhenClosed, hud.getActionBarCalls().size(),
                "a closed world's screen got ticks after the close: " + textsOnScreen(hud));
        Plugins.forget(Plugins.CORE);
        world = null;
    }

    @Test
    void aQueueWritesOnlyToThePlatformItStartedOn() throws InterruptedException {
        serverWithActionBar(true, false);
        Notifier.get().notify(player, bar("mana", "§9mana 40/40", 0));

        try (ECoreTestWorld next = Platforms.lenient().install()) {
            TestHud nextScreen = next.platform().getTestHud();
            Thread.sleep(3 * QueuedHudChannel.TICK_MILLIS);

            assertTrue(nextScreen.getActionBarCalls().isEmpty(),
                    "the queue of the first platform wrote on the second: " + textsOnScreen(nextScreen));
            assertTrue(channel().getQueued(player).isEmpty(), "the second platform starts with an empty queue");
            assertTrue(hud.getActionBarCalls().size() > 1, "while the first platform's queue kept ticking on its own screen");
        }
    }

    /**
     * A message one test leaves on the queue must not reach the next test: two cases in order, on the real scheduler,
     * the first ending with a 30-second bar still queued.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Isolation {

        @Test
        @Order(1)
        void aTestLeavesABarPending() {
            serverWithActionBar(true, false);
            Notifier.get().notify(player, bar("left-behind", "§eleft behind", 0));

            assertEquals(1, channel().getQueued(player).size());
        }

        @Test
        @Order(2)
        void theNextTestStartsWithAnEmptyQueue() throws InterruptedException {
            serverWithActionBar(true, false);

            assertTrue(channel().getQueued(player).isEmpty(), "inherited " + channel().getQueued(player));
            Thread.sleep(3 * QueuedHudChannel.TICK_MILLIS);
            assertTrue(hud.getActionBarCalls().isEmpty(), "the last test's bar reached this one: " + textsOnScreen(hud));
        }
    }
}
