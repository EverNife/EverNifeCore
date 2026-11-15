package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.config.plugin.CooldownSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /** The server's retention, for a plugin that declares none. */
    private static volatile long defaultRetentionMillis = TimeUnit.DAYS.toMillis(30);

    /** Each plugin's own retention, by plugin name; see {@link #configure(ECPluginData, CooldownSettings)}. */
    private static final Map<String, Owned> OWNERS = new ConcurrentHashMap<>();

    private CooldownRetention() {
    }

    /** Installs the server's retention in days ({@code Settings.Cooldown.RETENTION_DAYS}); until then 30 days apply. */
    public static void setDefaultDays(int days) {
        defaultRetentionMillis = TimeUnit.DAYS.toMillis(Math.max(0, days));
    }

    /**
     * Installs {@code owner}'s retention from its {@code cooldowns} block, replacing what it declared before: its
     * {@code retention-days} for its counters and {@code retention-overrides} per id. Only {@code owner}'s counters
     * read it - the same id of another plugin keeps that plugin's. Dropped when the plugin is released.
     */
    public static void configure(ECPluginData owner, CooldownSettings settings) {
        Integer days = settings.getRetentionDays();
        Map<String, Long> overrides = new HashMap<>();
        settings.getRetentionOverrides().forEach((id, overrideDays) -> overrides.put(id, TimeUnit.DAYS.toMillis(Math.max(0, overrideDays))));
        OWNERS.put(owner.getMetaInfo().getName(), new Owned(days == null ? null : TimeUnit.DAYS.toMillis(Math.max(0, days)), overrides));
    }

    /** Drops what {@code owner} declared; its counters fall back to the server's retention. */
    public static void forget(ECPluginData owner) {
        OWNERS.remove(owner.getMetaInfo().getName());
    }

    /** The retention window, in millis, of the counter {@code id} of the plugin named {@code pluginName}. */
    public static long retentionMillisFor(String pluginName, String id) {
        Owned owned = OWNERS.get(pluginName);
        if (owned == null) {
            return defaultRetentionMillis;
        }
        Long override = owned.overrides.get(id);
        if (override != null) {
            return override;
        }
        return owned.days != null ? owned.days : defaultRetentionMillis;
    }

    /** The retention window, in millis, of the counter stored under {@code key} ({@code <plugin>#<id>}). */
    public static long retentionMillisFor(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? defaultRetentionMillis : retentionMillisFor(key.substring(0, hash), key.substring(hash + 1));
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
     * Removes every entry past its retention horizon at {@code now}, each judged by the retention of
     * its own key's plugin and id. Returns whether anything was removed, so a caller can persist the shrink.
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

    private static final class Owned {
        final @Nullable Long days;
        final Map<String, Long> overrides;

        Owned(@Nullable Long days, Map<String, Long> overrides) {
            this.days = days;
            this.overrides = overrides;
        }
    }
}
