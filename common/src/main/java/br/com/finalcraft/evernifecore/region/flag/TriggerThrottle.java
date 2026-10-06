package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.region.RegionTime;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a flag's {@link RegionFlag#onApplied} fire at most once per player every
 * {@link FlagSettings#getTriggerInterval()}. Memory only: the window is read from the settings on every call, so a
 * reload changes the pace at once.
 */
public final class TriggerThrottle {

    private final ConcurrentHashMap<UUID, Map<FlagKey, Instant>> lastFired = new ConcurrentHashMap<>();

    /** True - and the firing is recorded - when {@code flag} may fire for {@code player} now. */
    public boolean tryFire(UUID player, RegionFlag<?> flag) {
        Duration interval = flag.getSettings().getTriggerInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return true;
        }
        Instant now = RegionTime.now();
        boolean[] fired = {false};
        lastFired.computeIfAbsent(player, uuid -> new ConcurrentHashMap<>()).compute(flag.getKey(), (key, last) -> {
            if (last != null && now.isBefore(last.plus(interval))) {
                return last;
            }
            fired[0] = true;
            return now;
        });
        return fired[0];
    }

    /** Drops everything recorded for {@code player}; called when the player leaves. */
    public void forget(UUID player) {
        lastFired.remove(player);
    }
}
