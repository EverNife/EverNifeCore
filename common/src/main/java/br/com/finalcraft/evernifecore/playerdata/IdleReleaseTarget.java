package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.everydatabase.manager.CachingManager;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What the idle sweep needs from a binding, whichever family it belongs to: the cache to scan, how
 * long an unused cell may stay, whether a key still has someone using it, and how to persist one
 * that is dirty.
 *
 * <p>A PDSection cell is keyed by a player uuid and stays while that player is online; an
 * AccountSection row is keyed by an accountId and stays while ANY member of that account is online.
 * Both need the same thing from the sweep - the cell loaded for someone who never logs in here
 * (an offline lookup, a bulk read) gets no quit event, so without a sweep it never leaves memory.</p>
 */
final class IdleReleaseTarget {

    /** Identifies this target across sweeps - the section class, unique across both families. */
    private final Class<?> sectionClass;
    private final CachingManager<UUID, ? extends StoredSection> manager;
    private final long graceMillis;
    private final Predicate<UUID> keyStillInUse;
    private final Consumer<UUID> flushDirty;

    IdleReleaseTarget(Class<?> sectionClass, CachingManager<UUID, ? extends StoredSection> manager,
                      long graceMillis, Predicate<UUID> keyStillInUse, Consumer<UUID> flushDirty) {
        this.sectionClass = sectionClass;
        this.manager = manager;
        this.graceMillis = graceMillis;
        this.keyStillInUse = keyStillInUse;
        this.flushDirty = flushDirty;
    }

    Class<?> getSectionClass() {
        return sectionClass;
    }

    CachingManager<UUID, ? extends StoredSection> getManager() {
        return manager;
    }

    long getGraceMillis() {
        return graceMillis;
    }

    /** Whether someone is still using this key here, which is what stops its grace from starting. */
    boolean isStillInUse(UUID key) {
        return keyStillInUse.test(key);
    }

    /** Persists an unflushed cell so a later sweep may release it - a dirty cell is never dropped. */
    void flushDirty(UUID key) {
        flushDirty.accept(key);
    }
}
