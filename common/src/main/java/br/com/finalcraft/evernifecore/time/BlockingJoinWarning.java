package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.EverNifeCore;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Makes a future the core hands out warn when a {@code join()} or {@code get()} on it blocks a server thread
 * ({@link ECScheduler#isServerThread()}): once the wait ends, one WARN line names the frame that called it and how
 * many milliseconds the server stood still. A future that is already complete, or a wait on any other thread, costs
 * one check and logs nothing. The futures derived from a watched one ({@code thenApply}, {@code thenCompose}, ...)
 * are watched too. The type a caller sees stays {@link CompletableFuture}.
 *
 * <p>One line per calling frame per {@value #WINDOW_SECONDS} s: the waits in between are counted and the next line
 * carries the count and the longest of them.</p>
 */
public final class BlockingJoinWarning {

    static final long WINDOW_SECONDS = 60;

    private static final Map<String, CallSite> CALL_SITES = new ConcurrentHashMap<>();

    private BlockingJoinWarning() {
    }

    /** A new, incomplete future that warns when it blocks a server thread. */
    public static <T> CompletableFuture<T> newFuture() {
        return new Watched<>();
    }

    /**
     * {@code future} as one that warns when it blocks a server thread: itself when it is already complete or
     * already watched, otherwise a watched future that completes as it does.
     */
    public static <T> CompletableFuture<T> watch(CompletableFuture<T> future) {
        if (future == null || future.isDone() || future instanceof Watched) {
            return future;
        }
        Watched<T> watched = new Watched<>();
        future.whenComplete((value, failure) -> {
            if (failure == null) {
                watched.complete(value);
            } else {
                watched.completeExceptionally(failure);
            }
        });
        return watched;
    }

    /** The start of a wait worth measuring, or -1 when the caller is not a server thread. */
    private static long begin() {
        return ECScheduler.isServerThread() ? System.nanoTime() : -1L;
    }

    private static void end(long startNanos, String verb) {
        if (startNanos < 0) {
            return;
        }
        long blockedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        String caller = callerFrame();
        CALL_SITES.computeIfAbsent(caller, key -> new CallSite()).blocked(verb, caller, blockedMillis);
    }

    //the frame that called join()/get(): the first one outside this class and java.util.concurrent
    private static String callerFrame() {
        String self = BlockingJoinWarning.class.getName();
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String className = frame.getClassName();
            boolean ours = className.equals(self) || className.startsWith(self + "$");
            if (!ours && !className.startsWith("java.util.concurrent.")) {
                return frame.toString();
            }
        }
        return "(unknown caller)";
    }

    private static final class CallSite {

        private long windowStartMillis;
        private boolean reported;
        private int silenced;
        private long longestSilencedMillis;

        synchronized void blocked(String verb, String caller, long blockedMillis) {
            long now = ECClock.now().toEpochMilli();
            if (reported && now - windowStartMillis < TimeUnit.SECONDS.toMillis(WINDOW_SECONDS)) {
                silenced++;
                longestSilencedMillis = Math.max(longestSilencedMillis, blockedMillis);
                return;
            }
            String earlier = silenced == 0 ? "" : " (and " + silenced + " more time(s) from there since the last"
                    + " warning, the longest " + longestSilencedMillis + " ms)";
            EverNifeCore.getLog().warning("A {}() on an EverNifeCore future blocked the server thread for {} ms at {}{}."
                            + " The server cannot tick while it waits: take the value with thenAccept(...) and come back"
                            + " with ECScheduler.onMainThread(...) instead of calling {}() there.",
                    verb, blockedMillis, caller, earlier, verb);
            reported = true;
            windowStartMillis = now;
            silenced = 0;
            longestSilencedMillis = 0;
        }
    }

    /**
     * The watched future. On Java 9+ {@link #newIncompleteFuture()} makes every derived stage one of these; the
     * overrides below do it for the common derivations on Java 8, which has no such hook.
     */
    private static final class Watched<T> extends CompletableFuture<T> {

        @Override
        public T join() {
            if (isDone()) {
                return super.join();
            }
            long start = begin();
            try {
                return super.join();
            } finally {
                end(start, "join");
            }
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            if (isDone()) {
                return super.get();
            }
            long start = begin();
            try {
                return super.get();
            } finally {
                end(start, "get");
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (isDone()) {
                return super.get(timeout, unit);
            }
            long start = begin();
            try {
                return super.get(timeout, unit);
            } finally {
                end(start, "get");
            }
        }

        //overrides CompletableFuture.newIncompleteFuture on Java 9+; an unused method on Java 8
        public <U> CompletableFuture<U> newIncompleteFuture() {
            return new Watched<>();
        }

        // reason-detre: on a Java 8 runtime only these derivations stay watched (the async and either/both forms do not); covering them means overriding each one here
        @Override
        public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
            return watch(super.thenApply(fn));
        }

        @Override
        public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
            return watch(super.thenAccept(action));
        }

        @Override
        public CompletableFuture<Void> thenRun(Runnable action) {
            return watch(super.thenRun(action));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
                                                       BiFunction<? super T, ? super U, ? extends V> fn) {
            return watch(super.thenCombine(other, fn));
        }

        @Override
        public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            return watch(super.thenCompose(fn));
        }

        @Override
        public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
            return watch(super.handle(fn));
        }

        @Override
        public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            return watch(super.whenComplete(action));
        }

        @Override
        public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
            return watch(super.exceptionally(fn));
        }
    }
}
