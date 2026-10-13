package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionAgenda;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.Resolution;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagHandler;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcome;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcomeContext;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Revalidation;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies the {@link FlagCategory#SESSION} flags to a player: {@code onEnter} when a value starts to apply,
 * {@code onLeave} when it stops, {@code onRevalidate} when it changes or its check comes due. When a flag is looked at
 * again follows its {@link Revalidation}; the checks that come due on their own run on a light tick that is only
 * scheduled while some check is pending, each world's on that world's thread.
 */
final class SessionFlags {

    static final long TICK_MILLIS = 1000;   //20 ticks

    enum Trigger {
        /** The set of regions changed, or the session was invalidated: every flag is looked at. */
        BOUNDARY,
        /** The player moved to another block inside the same regions: only the flags that check on every lookup. */
        LOOKUP,
        /** The light tick: the flags whose check came due. */
        DUE
    }

    private final RegionLookup lookup;
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final Set<String> unreachableWorlds = ConcurrentHashMap.newKeySet();
    private volatile boolean woken;

    SessionFlags(RegionLookup lookup) {
        this.lookup = lookup;
    }

    void apply(FPlayer player, RegionPlayerSession session, Trigger trigger) {
        Instant now = RegionTime.now();
        boolean invalidated = session.takeRevalidateAll();
        boolean all = invalidated || trigger == Trigger.BOUNDARY;
        //a crossing applies what changed; a check that was asked for - by policy or by an invalidation - revalidates even an unchanged value
        boolean asked = invalidated || trigger != Trigger.BOUNDARY;
        for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
            if (flag.getCategory() != FlagCategory.SESSION) {
                continue;
            }
            Revalidation.Kind kind = flag.getRevalidation().getKind();
            if (all || session.isDue(flag.getKey(), now) || (trigger == Trigger.LOOKUP && kind == Revalidation.Kind.EVERY_LOOKUP)) {
                evaluate(player, session, flag, now, asked);
            }
        }
        if (session.hasPendingCheck()) {
            wake();
        }
    }

    /** @param revalidateSame whether an unchanged value is revalidated anyway - a check that came due on its own */
    private <T> void evaluate(FPlayer player, RegionPlayerSession session, RegionFlag<T> flag, Instant now, boolean revalidateSame) {
        FlagKey key = flag.getKey();
        Resolution<T> resolution = lookup.resolution(flag, session.getCurrent(), player);
        Region source = resolution.getSource();
        T value = source == null ? null : resolution.getValue();
        @SuppressWarnings("unchecked")
        T applied = (T) session.getAppliedValues().get(key);
        Region appliedSource = session.getAppliedSource(key);

        if (applied == null && value == null) {
            session.setNextRevalidation(key, null);
            return;
        }
        if (applied == null) {
            session.setApplied(key, value, source);
            flag.onEnter(new SessionContext(player, source), value);
            notifyHandler(player, source, flag, value, FlagOutcome.APPLIED, resolution.getReason());
        } else if (value == null) {
            session.setApplied(key, null, null);
            flag.onLeave(new SessionContext(player, appliedSource), applied);
            notifyHandler(player, appliedSource, flag, applied, FlagOutcome.REMOVED, resolution.getReason());
        } else if (revalidateSame || !applied.equals(value)) {
            session.setApplied(key, value, source);
            flag.onRevalidate(new SessionContext(player, source), applied, value);
            if (!applied.equals(value)) {
                notifyHandler(player, source, flag, value, FlagOutcome.APPLIED, resolution.getReason());
            }
        }
        session.setNextRevalidation(key, value == null ? null : nextCheck(player, flag, source, now));
    }

    private static @Nullable Instant nextCheck(FPlayer player, RegionFlag<?> flag, Region source, Instant now) {
        Revalidation revalidation = flag.getRevalidation();
        switch (revalidation.getKind()) {
            case INTERVAL:
                Duration override = source.getEffectiveFlagSettings(flag).getRevalidationInterval();
                return now.plus(override != null ? override : revalidation.getInterval());
            case CUSTOM:
                return revalidation.nextCheckAt(new SessionContext(player, source));
            default:
                return null;
        }
    }

    /** The player left: every applied value stops applying. */
    void leaveAll(FPlayer player, RegionPlayerSession session) {
        for (Map.Entry<FlagKey, Object> entry : new HashMap<>(session.getAppliedValues()).entrySet()) {
            RegionFlag<?> flag = FlagRegistry.get().get(entry.getKey());
            Region source = session.getAppliedSource(entry.getKey());
            session.setApplied(entry.getKey(), null, null);
            session.setNextRevalidation(entry.getKey(), null);
            if (flag != null && source != null) {
                leave(player, flag, source, entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void leave(FPlayer player, RegionFlag<T> flag, Region source, Object lastValue) {
        flag.onLeave(new SessionContext(player, source), (T) lastValue);
        notifyHandler(player, source, flag, lastValue, FlagOutcome.REMOVED, Resolution.Step.Reason.DEFAULT);
    }

    private static void notifyHandler(FPlayer player, Region region, RegionFlag<?> flag, Object value, FlagOutcome outcome,
                                      Resolution.Step.Reason reason) {
        FlagHandler handler = region.getFlagHandler(flag);
        if (handler != null) {
            handler.handle(new FlagOutcomeContext(outcome, player, region, flag, value, EventSource.MOVE, null, reason, 0));
        }
    }

    // ------------------------------------------------------------------
    //  The light tick
    // ------------------------------------------------------------------

    /** Some check is pending: starts the tick when it is not running. */
    void wake() {
        woken = true;
        if (ticking.compareAndSet(false, true)) {
            RegionAgenda.scheduleAsync(this::tick, TICK_MILLIS);
        }
    }

    boolean isTicking() {
        return ticking.get();
    }

    void tick() {
        woken = false;
        Instant now = RegionTime.now();
        Map<String, List<RegionPlayerSession>> due = new HashMap<>();
        boolean pending = false;
        for (RegionPlayerSession session : lookup.getSessions()) {
            if (session.getPosition() == null || session.getOnlinePlayer() == null) {
                continue;
            }
            if (session.hasDueCheck(now)) {
                due.computeIfAbsent(session.getPosition().getWorldName(), world -> new ArrayList<>()).add(session);
            } else if (session.hasPendingCheck()) {
                pending = true;
            }
        }
        due.forEach((world, sessions) -> {
            CompletableFuture<Void> ran;
            try {
                ran = EverNifeCore.getPlatform().runOnWorldThread(world, () -> {
                    for (RegionPlayerSession session : sessions) {
                        FPlayer player = session.getOnlinePlayer();
                        if (player != null && lookup.getSessionIfPresent(session.getPlayer()) == session) {
                            apply(player, session, Trigger.DUE);
                        }
                    }
                });
            } catch (RuntimeException refused) {
                ran = new CompletableFuture<>();
                ran.completeExceptionally(refused);
            }
            ran.whenComplete((ignored, error) -> {
                if (error == null) {
                    unreachableWorlds.remove(world);
                } else if (unreachableWorlds.add(world)) {
                    ECDebugModule.REGIONS.debug("Session checks in world '{}' wait for the next tick: {}", world, error.toString());
                }
            });
        });
        if (pending || !due.isEmpty()) {
            RegionAgenda.scheduleAsync(this::tick, TICK_MILLIS);
            return;
        }
        ticking.set(false);
        if (woken && ticking.compareAndSet(false, true)) {
            RegionAgenda.scheduleAsync(this::tick, TICK_MILLIS);
        }
    }
}
