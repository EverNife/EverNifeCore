package br.com.finalcraft.evernifecore.cooldown;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The retention horizon a stored cooldown is pruned by, and the pruning itself. A stored map of
 * cooldowns only ever grows as new ids are used, so entries are dropped once they can no longer
 * affect any read: a player's bucket is pruned when read or converged, the rows of
 * {@link Cooldowns#of(br.com.finalcraft.evernifecore.ecplugin.ECPluginData, br.com.finalcraft.evernifecore.storage.ECStorage)}
 * once a minute.
 *
 * <p>The horizon is deliberately drawn PAST the nominal end. A read may reinterpret a cooldown's
 * anchor against a longer duration of its own, so an entry has to outlive its own duration by the
 * retention window; a read asking for a duration beyond that window is answered as free anyway, so
 * dropping the entry then changes no answer.</p>
 *
 * <p>A stopped cooldown - a zeroed anchor - has no nominal end to count from, so it is kept by its
 * last-mutation time instead. That distinction is load-bearing: the normal horizon with a zero
 * anchor is {@code 0 + duration + retention}, always in the past for a present-day clock, which
 * would make every stopped cooldown eligible on its first prune and let a peer's older start win
 * again by omission. Counting from the mutation clock gives the stop the same window in which a
 * lagging peer could still surface the old start - and the merge, deciding by that clock, keeps the
 * stop ahead of it for exactly that long.</p>
 */
public final class CooldownRetention {

    /** The retention applied to any id without an override. */
    private static volatile long defaultRetentionMillis = TimeUnit.DAYS.toMillis(30);

    /** Per-id retention overrides, in millis. Replaced wholesale by {@link #configure}. */
    private static volatile Map<String, Long> overridesMillis = Collections.emptyMap();

    private CooldownRetention() {
    }

    /**
     * Installs the admin-configured retention: a default (in days) and per-id overrides (in millis).
     * Called once as the settings are read; until then the built-in 30-day default applies.
     */
    public static void configure(int defaultDays, Map<String, Long> overridesById) {
        defaultRetentionMillis = TimeUnit.DAYS.toMillis(Math.max(0, defaultDays));
        overridesMillis = overridesById == null || overridesById.isEmpty()
                ? Collections.<String, Long>emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(overridesById));
    }

    /** The retention window, in millis, that applies to {@code identifier}. */
    public static long retentionMillisFor(String identifier) {
        Long override = overridesMillis.get(identifier);
        return override != null ? override : defaultRetentionMillis;
    }

    /**
     * Whether {@code entry} is past its retention horizon at {@code now}: a windowed count by the window's
     * end, a count without a window never, a stopped cooldown (zero anchor) by its mutation clock, any
     * other by its nominal end - each plus {@code retentionMillis}.
     */
    public static boolean isExpired(CooldownEntry entry, long now, long retentionMillis) {
        if (entry.getWindowEnd() != 0) {
            //the uses count only until the window turns: past it the entry answers nothing the gap does not
            return now > Math.max(entry.getWindowEnd(), entry.getTimeStart() + entry.getTimeDuration()) + retentionMillis;
        }
        if (entry.getUses() > 0) {
            return false; //a limit without a window counts forever: dropping it would hand the uses back
        }
        if (entry.getTimeStart() == 0) {
            return now > entry.getUpdatedAt() + retentionMillis;
        }
        return now > entry.getTimeStart() + entry.getTimeDuration() + retentionMillis;
    }

    /**
     * Removes every entry past its retention horizon at {@code now}, each judged by its own id's
     * window. Returns whether anything was removed, so a caller can persist the shrink.
     */
    public static boolean prune(Map<String, CooldownEntry> cooldowns, long now) {
        if (cooldowns.isEmpty()) {
            return false;
        }
        boolean removed = false;
        Iterator<Map.Entry<String, CooldownEntry>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CooldownEntry> mapEntry = it.next();
            if (isExpired(mapEntry.getValue(), now, retentionMillisFor(mapEntry.getKey()))) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }
}
