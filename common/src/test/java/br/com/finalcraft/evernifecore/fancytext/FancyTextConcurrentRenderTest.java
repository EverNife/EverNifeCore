package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A message lives in a static field and is rendered once per recipient, so two recipients are
 * routinely rendered from two threads. Rendering must therefore write nothing onto the message: no
 * trailing colour, no cached component, no resolved value.
 */
public class FancyTextConcurrentRenderTest {

    private static final int ITERATIONS = 200;

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @Test
    void twoThreadsRenderingTheSameChainEachGetTheirOwnRecipientsValues() throws Exception {
        // Several pieces, each ending in a colour the next one has to inherit - the chaining that
        // used to travel through a field on the leaf.
        FancyFormatter shared = FancyFormatter.of("§aWelcome ")
                .append("${who}")
                .append("§7 - rank ")
                .append("${rank}")
                .append("§7!");
        shared.addParser("who", context -> context.getSender().getName());
        shared.addParser("rank", context -> context.getSender().hasPermission("staff") ? "§cstaff" : "§bplayer");

        TestCommandSender alpha = new TestCommandSender("ALPHA").grant("staff");
        TestCommandSender beta = new TestCommandSender("BETA");

        String expectedAlpha = shared.toLegacyString(RenderContext.of(alpha));
        String expectedBeta = shared.toLegacyString(RenderContext.of(beta));
        assertNotEquals(expectedAlpha, expectedBeta,
                "the two recipients must not agree by accident, or the test proves nothing");

        // Both threads render the same round at the same moment - no sleeping, so the test is
        // deterministic and still forces the overlap it exists to observe.
        CyclicBarrier sameRound = new CyclicBarrier(2);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicReference<Throwable> alphaFailure = new AtomicReference<>();
        AtomicReference<Throwable> betaFailure = new AtomicReference<>();
        List<String> alphaRenders = new ArrayList<>(ITERATIONS);
        List<String> betaRenders = new ArrayList<>(ITERATIONS);

        Thread alphaThread = renderingThread(shared, alpha, alphaRenders, sameRound, finished, alphaFailure);
        Thread betaThread = renderingThread(shared, beta, betaRenders, sameRound, finished, betaFailure);

        alphaThread.start();
        betaThread.start();
        finished.await();
        alphaThread.join();
        betaThread.join();

        assertAll(
                () -> assertNull(alphaFailure.get(), "rendering for ALPHA threw"),
                () -> assertNull(betaFailure.get(), "rendering for BETA threw"),
                () -> assertEquals(ITERATIONS, alphaRenders.size()),
                () -> assertEquals(ITERATIONS, betaRenders.size()),
                () -> assertEquals(1, alphaRenders.stream().distinct().count(),
                        "ALPHA saw more than one result: " + alphaRenders.stream().distinct().collect(Collectors.toList())),
                () -> assertEquals(1, betaRenders.stream().distinct().count(),
                        "BETA saw more than one result: " + betaRenders.stream().distinct().collect(Collectors.toList())),
                () -> assertEquals(expectedAlpha, alphaRenders.get(0)),
                () -> assertEquals(expectedBeta, betaRenders.get(0))
        );
    }

    private static Thread renderingThread(FancyText shared,
                                          TestCommandSender recipient,
                                          List<String> renders,
                                          CyclicBarrier sameRound,
                                          CountDownLatch finished,
                                          AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    sameRound.await();
                    renders.add(shared.toLegacyString(RenderContext.of(recipient)));
                }
            } catch (Throwable t) {
                failure.set(t);
                sameRound.reset();   //the peer must not block forever waiting for a thread that died
            } finally {
                finished.countDown();
            }
        }, "render-" + recipient.getName());
        thread.setDaemon(true);
        return thread;
    }
}
