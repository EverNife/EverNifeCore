package br.com.finalcraft.evernifecore.conformance;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/** One check against the live server. A {@link #run()} that throws is reported as a FAIL naming the exception. */
public interface ConformanceCase {

    String getId();

    Verdict run() throws Exception;

    /** How the runner asks: the answer now, or a future one the run waits for without holding the thread. */
    default CompletableFuture<Verdict> start() throws Exception {
        return CompletableFuture.completedFuture(run());
    }

    static ConformanceCase of(String id, Callable<Verdict> body) {
        return new ConformanceCase() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Verdict run() throws Exception {
                return body.call();
            }
        };
    }

    /**
     * A case whose answer comes on a later tick - a task the scheduler runs, a screen that opens next tick. The
     * future has to complete on its own, its timeout included: the runner waits for it as long as it takes.
     */
    static ConformanceCase later(String id, Callable<CompletableFuture<Verdict>> body) {
        return new ConformanceCase() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Verdict run() {
                throw new IllegalStateException(id + " answers on a later tick; the runner asks it through start()");
            }

            @Override
            public CompletableFuture<Verdict> start() throws Exception {
                return body.call();
            }
        };
    }
}
