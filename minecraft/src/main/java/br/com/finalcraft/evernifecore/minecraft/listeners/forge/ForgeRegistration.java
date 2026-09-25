package br.com.finalcraft.evernifecore.minecraft.listeners.forge;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One listener registered through {@link ForgeListener}. {@link #unregister()} takes it off every bus that
 * registration reached, so a plugin can undo its Forge listeners in its own shutdown instead of asking for a
 * server restart.
 */
public interface ForgeRegistration {

    /**
     * Stops delivering Forge events to the listener this registration was made for. Only the first call does
     * anything; a later one is a no-op.
     *
     * @throws IllegalStateException if the hybrid under this server offers no way to take the listener off a
     *                               bus it reached - the message names the bus and the member it looked for.
     */
    void unregister();

    /** Wraps {@code undo} so it runs at most once, whatever the bus underneath does with a second call. */
    static ForgeRegistration once(Runnable undo){
        AtomicBoolean done = new AtomicBoolean(false);
        return () -> {
            if (done.compareAndSet(false, true)){
                undo.run();
            }
        };
    }
}
