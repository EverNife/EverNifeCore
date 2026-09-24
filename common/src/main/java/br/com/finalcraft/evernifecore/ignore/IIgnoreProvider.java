package br.com.finalcraft.evernifecore.ignore;

import java.util.UUID;

/**
 * Answers whether one player ignores another, for the single {@link IgnoreKind} it was registered under.
 *
 * <p>Must be thread-safe: chat is checked off the main thread, once per recipient per line, so the
 * answer should come from memory, never from storage. Staff bypass and any other policy belong here -
 * the core only relays the answer.</p>
 */
@FunctionalInterface
public interface IIgnoreProvider {

    /** The answer when no plugin implements a kind: nobody ignores anybody. */
    IIgnoreProvider NOBODY = (ignorer, ignored) -> false;

    boolean isIgnoring(UUID ignorer, UUID ignored);
}
