package br.com.finalcraft.evernifecore.minecraft.chat;

import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long a wait for chat lasts, and what ends it.
 *
 * <p>Three readings of one contract: an expiration of zero is "wait indefinitely", an expiration that
 * was really asked for still runs out, and an expiration with nothing to run when it elapses is never
 * scheduled and is swept the next time that player types.</p>
 *
 * <p>The expiration is armed on the core's own scheduler, so a real background thread is what fires
 * it - which is why the waiting here is on a latch and not on the test's own clock.</p>
 */
class ChatExpectationListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    /** Short enough that a wait really does run out inside a test, long enough not to race one. */
    private static final long SOON_MILLIS = 50L;

    /** Long enough that a timeout armed on zero delay would have fired many times over. */
    private static final long LONG_ENOUGH_TO_TELL_MILLIS = 200L;

    private GuiTestWorld world;
    private PlayerDouble player;

    /** Every message the wait under test was offered. */
    private final List<String> heard = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        player = world.newPlayer("Steve");
    }

    @AfterEach
    void teardown() {
        //a wait left outstanding keeps a task on the core's real scheduler, which would fire into a
        //world that no longer exists
        for (ExpectedChat outstanding : waitsOn()) {
            ChatExpectationListener.get().stopExpecting(outstanding);
        }
        if (world != null) {
            world.close();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The wait
    // -----------------------------------------------------------------------------------------------------------------

    private ExpectedChat waitFor(long expiration, Runnable onExpireAction) {
        return ChatExpectationListener.get().expectPlayerChat(player.asPlayer(), message -> {
            heard.add(message);
            return IChatAction.ActionResult.SUCCESS_AND_CONSUME;
        }, expiration, onExpireAction, null);
    }

    private List<ExpectedChat> waitsOn() {
        return new ArrayList<>(ChatExpectationListener.get().getChatListeners().get(player.getUniqueId()));
    }

    /** Runs the real clock until {@code waiting} is past its expiration, or fails the test. */
    private void awaitExpiry(ExpectedChat waiting) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!waiting.hasExpired()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("The wait never ran out");
            }
            sleep(1L);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting on the clock", interrupted);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The cases
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anExpirationOfZeroWaitsIndefinitelyInsteadOfRunningOutAtOnce() throws InterruptedException {
        CountDownLatch ranOut = new CountDownLatch(1);

        ExpectedChat waiting = waitFor(0L, ranOut::countDown);

        assertFalse(ranOut.await(LONG_ENOUGH_TO_TELL_MILLIS, TimeUnit.MILLISECONDS),
                "zero is the caller asking to wait for as long as it takes; a timeout armed on a delay of "
                        + "zero fires on the same tick and hands them the opposite");
        assertNull(waiting.getFuture().get(), "a wait that never runs out has nothing to schedule");
        assertFalse(waiting.hasExpired());
        assertTrue(waiting.isWaitingForResponse());
        assertTrue(ChatExpectationListener.get().hasAnyExpectedChat(player.asPlayer()));

        world.getEvents().typeInChat(player.asPlayer(), "here it is");

        assertEquals(Arrays.asList("here it is"), heard, "and it is still the one the message goes to");
        assertTrue(waitsOn().isEmpty(), "which settles it");
    }

    @Test
    void anExpirationThatWasAskedForStillRunsOut() throws InterruptedException {
        CountDownLatch ranOut = new CountDownLatch(1);

        ExpectedChat waiting = waitFor(SOON_MILLIS, ranOut::countDown);

        assertTrue(ranOut.await(5_000L, TimeUnit.MILLISECONDS), "the expiration is what the caller asked for");
        assertTrue(waiting.hasExpired());
        assertFalse(waiting.isWaitingForResponse());

        world.getEvents().typeInChat(player.asPlayer(), "too late");

        assertEquals(Collections.emptyList(), heard, "a wait that ran out gets no answer");
        assertTrue(waitsOn().isEmpty());
    }

    @Test
    void aWaitWithNothingToRunOnExpiryIsNeverScheduledAndIsSweptTheNextTimeThatPlayerTypes() {
        ExpectedChat waiting = waitFor(SOON_MILLIS, null);

        assertNull(waiting.getFuture().get(), "there is nothing to run when it elapses, so nothing is armed");

        awaitExpiry(waiting);

        assertTrue(ChatExpectationListener.get().hasAnyExpectedChat(player.asPlayer()),
                "nothing came along to notice it had run out");

        world.getEvents().typeInChat(player.asPlayer(), "too late");

        assertEquals(Collections.emptyList(), heard, "a wait that ran out gets no answer");
        assertTrue(waitsOn().isEmpty(), "the message that found it expired is what sweeps it");
    }

}
