package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two main-thread bridges of {@link McPlatform}, told apart by when they run:
 * {@code runOnMainThread} is in place when the caller already is the main thread and a hop when it
 * is not; {@code runOnMainThreadNextTick} always waits for the tick, even from the main thread -
 * which is what lets a task scheduled during enable fire only after every plugin has enabled.
 */
class McPlatformMainThreadTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void alreadyOnTheMainThreadThereIsNothingToWaitFor() {
        try (GuiTestWorld world = GuiTestWorld.install(tempDir)) {
            McPlatform platform = new McPlatform();
            List<String> ran = new ArrayList<>();

            CompletableFuture<Void> hop = platform.runOnMainThread(() -> {
                ran.add("ran");
            });

            assertEquals(Arrays.asList("ran"), ran, "the task ran inside the call itself");
            assertTrue(hop.isDone());
            assertEquals("value", platform.runOnMainThread(() -> "value").getNow(null));
        }
    }

    @Test
    void offTheMainThreadTheTaskWaitsForTheTickAndLandsOnIt() {
        try (GuiTestWorld world = GuiTestWorld.install(tempDir)) {
            McPlatform platform = new McPlatform();
            AtomicReference<Thread> ranOn = new AtomicReference<>();

            CompletableFuture<Void> hop = offTheMainThread(
                    () -> platform.runOnMainThread(() -> ranOn.set(Thread.currentThread())));

            assertNull(ranOn.get(), "nothing may run on the worker that asked");
            assertFalse(hop.isDone());

            world.advanceTicks(1);

            assertSame(Thread.currentThread(), ranOn.get(), "the task landed on the main thread");
            assertTrue(hop.isDone());
        }
    }

    @Test
    void nextTickNeverRunsInsideTheCallEvenFromTheMainThread() {
        try (GuiTestWorld world = GuiTestWorld.install(tempDir)) {
            McPlatform platform = new McPlatform();
            List<String> order = new ArrayList<>();

            CompletableFuture<Void> tick = platform.runOnMainThreadNextTick(() -> {
                order.add("the task");
            });
            order.add("the rest of the call");

            assertFalse(tick.isDone(), "a task for the next tick has no business running inside this one");

            world.advanceTicks(1);

            assertEquals(Arrays.asList("the rest of the call", "the task"), order);
            assertTrue(tick.isDone());
        }
    }

    @Test
    void aTaskThatThrowsCompletesTheFutureExceptionally() {
        try (GuiTestWorld world = GuiTestWorld.install(tempDir)) {
            McPlatform platform = new McPlatform();

            CompletableFuture<Object> failed = platform.runOnMainThread((Supplier<Object>) () -> {
                throw new IllegalStateException("the task failed");
            });

            assertTrue(failed.isCompletedExceptionally(), "the failure reaches whoever chained on the future");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /** Runs {@code body} on a worker and waits it out - the rig calls only the installing thread main. */
    private static <T> T offTheMainThread(Callable<T> body) {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                value.set(body.call());
            } catch (Throwable throwable) {
                thrown.set(throwable);
            }
        }, "main-thread-bridge-worker");
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the worker", interrupted);
        }
        if (thrown.get() != null) {
            throw new AssertionError("The worker failed", thrown.get());
        }
        return value.get();
    }

}
