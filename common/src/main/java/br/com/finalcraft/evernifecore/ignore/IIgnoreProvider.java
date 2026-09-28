package br.com.finalcraft.evernifecore.ignore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Answers whether one player ignores another, for the single {@link IgnoreKind} it was registered under.
 *
 * <p>Must be thread-safe. Staff bypass and any other policy belong here - the core only relays the
 * answer.</p>
 */
@FunctionalInterface
public interface IIgnoreProvider {

    /** The answer when no plugin implements a kind: nobody ignores anybody. */
    IIgnoreProvider NOBODY = (ignorer, ignored) -> false;

    /**
     * The hot-path answer: chat asks it off the main thread, once per recipient per line, so it must
     * come from memory, never from storage. An ignorer whose data is not loaded may answer {@code false}.
     */
    boolean isIgnoring(UUID ignorer, UUID ignored);

    /**
     * The answer for a caller that can wait - an offline ignorer, a command, a queued request. A provider
     * may load what it needs here, off the calling thread, before completing. Defaults to
     * {@link #isIgnoring(UUID, UUID)}.
     */
    default CompletableFuture<Boolean> isIgnoringAsync(UUID ignorer, UUID ignored) {
        return CompletableFuture.completedFuture(isIgnoring(ignorer, ignored));
    }
}
