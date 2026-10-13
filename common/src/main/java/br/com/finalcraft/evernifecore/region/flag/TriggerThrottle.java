package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.region.RegionClock;
import jakarta.annotation.Nullable;

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

    /** True - and the firing is recorded - when {@code flag} may fire for {@code player} now, at its server-wide pace. */
    public boolean tryFire(UUID player, RegionFlag<?> flag) {
        return tryFire(player, flag, flag.getSettings().getTriggerInterval());
    }

    /** As {@link #tryFire(UUID, RegionFlag)}, at the pace of {@code interval} - a region's own settings; zero or null always fires. */
    public boolean tryFire(UUID player, RegionFlag<?> flag, @Nullable Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return true;
        }
        Instant now = RegionClock.now();
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
