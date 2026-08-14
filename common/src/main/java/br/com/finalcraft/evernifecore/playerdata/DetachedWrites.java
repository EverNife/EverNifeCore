package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports a write made on a section instance the cache no longer holds - the write the flush would
 * never see, because it persists the cached values and this instance is not one of them anymore.
 *
 * <p>Reported once per (section class, key): a plugin looping writes over a detached instance would
 * otherwise flood the console. The memory of what was already reported is capped, so the throttle
 * itself cannot grow without bound on a server where this keeps happening - the counter still
 * accumulates every occurrence.</p>
 */
final class DetachedWrites {

    /** Upper bound of distinct (class, key) pairs remembered for throttling. */
    private static final int MAX_TRACKED = 512;

    private static final Set<String> alreadyReported = ConcurrentHashMap.newKeySet();
    private static final AtomicLong occurrences = new AtomicLong();

    private DetachedWrites() {
    }

    static void report(StoredSection section, UUID key) {
        occurrences.incrementAndGet();
        String identity = section.getClass().getName() + "/" + key;
        if (alreadyReported.size() >= MAX_TRACKED || !alreadyReported.add(identity)) {
            return;
        }
        EverNifeCore.getLog().severe("LOST WRITE - markDirty() on a {} [{}] of [{}] that is no longer the cached"
                        + " instance, so the flush will never persist it. That cell was released or"
                        + " replaced: idle grace, cache TTL, the maxCached ceiling, a plugin"
                        + " re-registration/reload or clearPDSections. Do not hold a section reference"
                        + " between ticks - resolve it where you use it, which for an online player is"
                        + " an already-completed future.",
                section.sectionKind(), section.getClass().getName(), key);
    }

    /** Every detached write seen since boot, including the ones the throttle did not print. */
    static long occurrences() {
        return occurrences.get();
    }

    /** Drops the counter and the throttle memory. */
    static void reset() {
        occurrences.set(0);
        alreadyReported.clear();
    }
}
