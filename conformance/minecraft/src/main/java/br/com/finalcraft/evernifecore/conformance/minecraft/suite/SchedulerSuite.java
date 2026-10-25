package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The main-thread contract PlayerData and the item engine write into the world through: a task handed over from
 * another thread runs on the main one, and a "next tick" task never runs inline, even when asked from the main
 * thread itself.
 */
public final class SchedulerSuite implements ConformanceSuite {

    private static final long TIMEOUT_MILLIS = 5_000L;

    @Override
    public String getId() {
        return "scheduler";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.later("main-thread-from-async", () -> {
                    CompletableFuture<Verdict> answer = new CompletableFuture<>();
                    ECScheduler.runAsync(() -> {
                        boolean askedOffMain = !Bukkit.isPrimaryThread();
                        ECScheduler.onMainThread(() -> {
                            boolean ranOnMain = Bukkit.isPrimaryThread();
                            answer.complete(Verdict.of(askedOffMain && ranOnMain,
                                    "askedOffMain=" + askedOffMain + " ranOnMain=" + ranOnMain));
                        });
                    });
                    return PlayerCases.within(answer, TIMEOUT_MILLIS);
                }),
                ConformanceCase.later("later-never-inline", () -> {
                    AtomicBoolean ran = new AtomicBoolean();
                    CompletableFuture<Verdict> answer = new CompletableFuture<>();
                    ECScheduler.onMainThreadLater(() -> {
                        ran.set(true);
                        answer.complete(Verdict.pass("ran later on main=" + Bukkit.isPrimaryThread()));
                    });
                    if (ran.get()) {
                        return CompletableFuture.completedFuture(Verdict.fail("ran inline, inside the call that scheduled it"));
                    }
                    return PlayerCases.within(answer, TIMEOUT_MILLIS);
                }),
                ConformanceCase.later("run-sync-never-inline", () -> {
                    AtomicBoolean ran = new AtomicBoolean();
                    CompletableFuture<Verdict> answer = new CompletableFuture<>();
                    ECScheduler.getMinecraftScheduler().runSync(() -> {
                        ran.set(true);
                        answer.complete(Verdict.of(Bukkit.isPrimaryThread(), "ran later on main=" + Bukkit.isPrimaryThread()));
                    });
                    if (ran.get()) {
                        return CompletableFuture.completedFuture(Verdict.fail("ran inline, inside the call that scheduled it"));
                    }
                    return PlayerCases.within(answer, TIMEOUT_MILLIS);
                }));
    }
}
