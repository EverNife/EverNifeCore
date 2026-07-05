package br.com.finalcraft.evernifecore.util;

/**
 * A guard that runs a given action at most once. The first {@link #run()} executes the action; every later
 * call is a no-op. Thread-safe: even under concurrent first calls the action runs a single time, and it is
 * marked done only AFTER it returns normally, so an action that throws can be retried on the next call.
 */
public final class ExecuteOnce {

    private final Runnable action;
    private volatile boolean done;

    private ExecuteOnce(final Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }
        this.action = action;
    }

    /** Wraps {@code action} in a run-at-most-once guard. */
    public static ExecuteOnce of(final Runnable action) {
        return new ExecuteOnce(action);
    }

    /** Runs the action on the first call only; later calls are no-ops. */
    public void run() {
        if (done) {
            return;
        }
        synchronized (this) {
            if (done) {
                return;
            }
            action.run();
            done = true;
        }
    }

    /** Whether the action has already run (successfully). */
    public boolean hasRun() {
        return done;
    }
}
