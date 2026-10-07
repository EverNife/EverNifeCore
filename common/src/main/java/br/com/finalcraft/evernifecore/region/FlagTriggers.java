package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.flag.FlagHandler;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcome;
import br.com.finalcraft.evernifecore.region.flag.FlagOutcomeContext;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.MoveContext;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.TriggerThrottle;
import br.com.finalcraft.evernifecore.region.flag.stock.MoveDenySettings;
import br.com.finalcraft.evernifecore.region.session.MoveVerdict;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.spawn.SpawnPoints;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Internal: the one place a protection flag is asked about an action and its consequences run - the flag's
 * {@link RegionFlag#onApplied} at the pace of its settings, then the region's {@link FlagHandler}, and for a denied
 * movement the way back: the handler's choice, else the region's settings of the flag, else the server's, else the
 * last accepted block, moved further back along the trail for a player who keeps being denied.
 */
public final class FlagTriggers {

    /** The throttle key of an action no player is behind: one firing per interval for all of them. */
    static final UUID NO_PLAYER = new UUID(0, 0);

    private static final TriggerThrottle THROTTLE = new TriggerThrottle();
    private static final Duration DEFAULT_STUCK_WINDOW = Duration.ofSeconds(1);

    private FlagTriggers() {
    }

    static Decision decide(RegionLookup lookup, ActionContext context) {
        List<Region> here = lookup.at(context.getPosition());
        if (here.isEmpty()) {
            return Decision.PASS;
        }
        List<Runnable> passed = new ArrayList<>(2);
        for (RegionFlag<?> flag : FlagRegistry.get().getBySource(context.getSource())) {
            if (flag.getCategory() == FlagCategory.PROTECTION && isAsked(flag, context) && denies(lookup, flag, context, here, passed)) {
                return Decision.DENY;
            }
        }
        for (Runnable apply : passed) {
            apply.run();
        }
        return Decision.PASS;
    }

    private static <T> boolean denies(RegionLookup lookup, RegionFlag<T> flag, ActionContext context, List<Region> here, List<Runnable> passed) {
        FPlayer player = context.getPlayer();
        Climb<T> climb = climb(flag, asked -> lookup.resolution(asked, here, player));
        Resolution<T> resolution = climb.resolution;
        T value = resolution.getValue();
        if (value == null) {
            return false;
        }
        Region source = resolution.getSource();
        if (flag.decide(context, value, climb.getSettings(null)) == Decision.DENY) {
            fireApplied(flag, context, value, source);
            FlagHandler handler = source == null ? null : source.getFlagHandler(flag);
            if (handler != null) {
                handler.handle(new FlagOutcomeContext(FlagOutcome.DENIED, player, source, flag, value, context.getSource(), context,
                        resolution.getReason(), 0));
            }
            return true;
        }
        if (source != null) {
            passed.add(() -> fireApplied(flag, context, value, source));
        }
        for (Region region : here) {
            if (region.hasFlagHandlers()) {
                notifyAllowed(lookup, flag, context, region, value, resolution);
            }
        }
        return false;
    }

    /**
     * Internal: what the movement flags say about crossing the border of {@code context.getRegion()} - only that
     * region's values decide, its neighbours have no say on it.
     */
    public static MoveVerdict decideCrossing(RegionLookup lookup, MoveContext context, RegionPlayerSession session) {
        for (RegionFlag<?> flag : FlagRegistry.get().getBySource(EventSource.MOVE)) {
            if (flag.getCategory() == FlagCategory.PROTECTION && isAsked(flag, context)) {
                MoveVerdict verdict = cross(lookup, flag, context, session);
                if (!verdict.isAllowed()) {
                    return verdict;
                }
            }
        }
        return MoveVerdict.allowed();
    }

    private static <T> MoveVerdict cross(RegionLookup lookup, RegionFlag<T> flag, MoveContext context, RegionPlayerSession session) {
        Region crossed = context.getRegion();
        FPlayer player = context.getPlayer();
        Climb<T> climb = climb(flag, asked -> lookup.explain(asked, crossed, player));
        Resolution<T> resolution = climb.resolution;
        T value = resolution.getValue();
        FlagSettings settings = climb.getSettings(crossed);
        if (value == null || flag.decide(context, value, settings) != Decision.DENY) {
            if (crossed.hasFlagHandlers()) {
                notifyAllowed(lookup, flag, context, crossed, value, resolution);
            }
            return MoveVerdict.allowed();
        }
        Region source = resolution.getSource() != null ? resolution.getSource() : crossed;
        fireApplied(flag, context, value, source);
        MoveDenySettings moveSettings = settings instanceof MoveDenySettings ? (MoveDenySettings) settings : null;
        int denials = session.noteDenial(flag.getKey(), RegionTime.now(), moveSettings != null ? moveSettings.getStuckWindow() : DEFAULT_STUCK_WINDOW);

        FlagHandler handler = source.getFlagHandler(flag);
        if (handler != null) {
            FlagOutcomeContext outcome = new FlagOutcomeContext(FlagOutcome.DENIED, player, source, flag, value, EventSource.MOVE, context,
                    resolution.getReason(), denials);
            handler.handle(outcome);
            if (outcome.isAllowed()) {
                return MoveVerdict.allowed();
            }
            if (outcome.getRevertTo() != null) {
                return MoveVerdict.revert(outcome.getRevertTo());
            }
            if (outcome.getRevertWith() != null) {
                return apply(outcome.getRevertWith(), session, context, denials, null);   //the handler saw the count: it decides
            }
        }
        RevertStrategy strategy = moveSettings != null ? moveSettings.getRevert() : RevertStrategy.lastValid();
        return apply(strategy, session, context, denials, handler == null ? moveSettings : null);
    }

    /** Where a point strategy sends the player, read in the world the player is in; null when the platform does not know it. */
    private static @Nullable WorldLocPos destinationOf(RevertStrategy strategy, MoveContext context) {
        String world = context.getFrom().getWorldName();
        switch (strategy.getKind()) {
            case FIXED:
                return strategy.getWorld() != null
                        ? strategy.getPoint()
                        : WorldLocPos.of(strategy.getX(), strategy.getY(), strategy.getZ(), world);
            case SPAWN:
                return SpawnPoints.getServerSpawn(context.getPlayer(), world);
            case WORLD_SPAWN:
                return SpawnPoints.getWorldSpawn(world);
            default:
                return null;
        }
    }

    /** @param stuck the settings that move a player who keeps being denied further back; null to never escalate */
    private static MoveVerdict apply(RevertStrategy strategy, RegionPlayerSession session, MoveContext context, int denials,
                                     @Nullable MoveDenySettings stuck) {
        int stepsBack;
        switch (strategy.getKind()) {
            case FIXED:
            case SPAWN:
            case WORLD_SPAWN:
                WorldLocPos destination = destinationOf(strategy, context);
                if (destination != null) {
                    return MoveVerdict.revert(destination);
                }
                ECDebugModule.REGIONS.debug("{} was denied by {} with revert {}, but the platform knows no such point in world {}; reverting to"
                        + " the last valid block instead.", context.getPlayer().getName(), context.getRegion().getId(), strategy, context.getFrom().getWorldName());
                stepsBack = 0;
                break;
            case ALLOW_AFTER:
                if (denials >= strategy.getCount()) {
                    return MoveVerdict.allowed();
                }
                stepsBack = 0;
                break;
            case TRAIL:
                stepsBack = strategy.getCount();
                break;
            default:
                stepsBack = 0;
        }
        if (stuck != null && strategy.getKind() != RevertStrategy.Kind.ALLOW_AFTER && denials >= stuck.getStuckAfter()) {
            stepsBack += denials - stuck.getStuckAfter() + 1;
            if (stepsBack >= session.getTrailSize()) {
                if (stuck.getStuckFallback() != null) {
                    return MoveVerdict.revert(stuck.getStuckFallback());
                }
                if (stepsBack == session.getTrailSize()) {
                    ECDebugModule.REGIONS.debug("{} keeps being denied by {} and the way back ran out; no stuck fallback is set, so it stays at"
                            + " the oldest block it walked.", context.getPlayer().getName(), context.getRegion().getId());
                }
            }
        }
        WorldLocPos point = session.trailBack(stepsBack);
        return MoveVerdict.revert(point != null ? point : context.getFrom());
    }

    /**
     * The handler of {@code region} hears {@link FlagOutcome#ALLOWED} when the region's own values would deny the
     * action but this player was not denied - a group or the bypass lifted it. Never for a region that does not
     * have the flag, nor for an action the flag would not deny anyway.
     */
    private static <T> void notifyAllowed(RegionLookup lookup, RegionFlag<T> flag, ActionContext context, Region region,
                                          @Nullable T effective, Resolution<T> resolution) {
        FlagHandler handler = region.getFlagHandler(flag);
        if (handler == null) {
            return;
        }
        Resolution<T> own = lookup.explain(flag, region, null);
        if (own.getSource() == null || own.getValue() == null || flag.decide(context, own.getValue(), region.getEffectiveFlagSettings(flag)) != Decision.DENY) {
            return;
        }
        handler.handle(new FlagOutcomeContext(FlagOutcome.ALLOWED, context.getPlayer(), region, flag, effective, context.getSource(), context,
                resolution.getReason(), 0));
    }

    /**
     * Whether {@code flag} is the one to ask about {@code context}: it applies, and no registered refinement of it
     * (at any depth) applies too - the most specific flag that speaks about the action answers for it.
     */
    private static boolean isAsked(RegionFlag<?> flag, ActionContext context) {
        return flag.applies(context) && !isRefinedFor(flag, context);
    }

    private static boolean isRefinedFor(RegionFlag<?> flag, ActionContext context) {
        for (RegionFlag<?> child : FlagRegistry.get().getChildren(flag)) {
            if (child.applies(context) || isRefinedFor(child, context)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The resolution {@code flag} reads: its own when a region or group defines it (or a bypass lifts it), else that
     * of its nearest registered ancestor that is defined, else its own default. A bypass met on the way up stops the
     * climb at {@code flag}'s default.
     */
    private static <T> Climb<T> climb(RegionFlag<T> flag, Function<RegionFlag<T>, Resolution<T>> resolve) {
        Resolution<T> own = resolve.apply(flag);
        if (own.getReason() != Resolution.Step.Reason.DEFAULT) {
            return new Climb<>(flag, flag, own);
        }
        for (RegionFlag<T> ancestor = flag.getParent(); ancestor != null && FlagRegistry.get().get(ancestor.getKey()) == ancestor;
             ancestor = ancestor.getParent()) {
            Resolution<T> theirs = resolve.apply(ancestor);
            if (theirs.getReason() == Resolution.Step.Reason.BYPASS) {
                break;
            }
            if (theirs.getReason() != Resolution.Step.Reason.DEFAULT) {
                return new Climb<>(flag, ancestor, theirs);
            }
        }
        return new Climb<>(flag, flag, own);
    }

    /** A flag's resolution, and which flag of its chain the value came from. */
    private static final class Climb<T> {
        private final RegionFlag<T> asked;
        private final RegionFlag<T> definer;
        private final Resolution<T> resolution;

        private Climb(RegionFlag<T> asked, RegionFlag<T> definer, Resolution<T> resolution) {
            this.asked = asked;
            this.definer = definer;
            this.resolution = resolution;
        }

        /**
         * The settings in effect where the value came from: those the region gave the flag that defined the value
         * (an inherited {@code only=[...]} narrows the refinement too) when they fit the asked flag, else the asked
         * flag's own; {@code whereUndefined}'s (or the server's, when null) when no region gave the value.
         */
        FlagSettings getSettings(@Nullable Region whereUndefined) {
            RegionFlag<T> owner = asked.getSettingsType().isAssignableFrom(definer.getSettingsType()) ? definer : asked;
            Region source = resolution.getSource() != null ? resolution.getSource() : whereUndefined;
            return source != null ? source.getEffectiveFlagSettings(owner) : owner.getSettings();
        }
    }

    private static <T> void fireApplied(RegionFlag<T> flag, ActionContext context, T value, @Nullable Region source) {
        FPlayer player = context.getPlayer();
        Duration interval = source != null ? source.getEffectiveFlagSettings(flag).getTriggerInterval() : flag.getSettings().getTriggerInterval();
        if (THROTTLE.tryFire(player == null ? NO_PLAYER : player.getUniqueId(), flag, interval)) {
            flag.onApplied(context, value);
        }
    }

    /** Internal: forgets the firing pace of a player who left. */
    public static void forget(UUID player) {
        THROTTLE.forget(player);
    }
}
