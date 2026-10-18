package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionClock;
import br.com.finalcraft.evernifecore.region.Resolution;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.flag.context.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.context.MoveContext;
import br.com.finalcraft.evernifecore.region.flag.core.EntryDenySettings;
import br.com.finalcraft.evernifecore.region.flag.core.MoveDenySettings;
import br.com.finalcraft.evernifecore.region.session.MoveVerdict;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.spawn.SpawnPoints;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Internal: the one place a protection flag is asked about an action and its consequences run - the flag's
 * {@link RegionFlag#onApplied} at the pace of its settings, then the region's {@link FlagHandler}, and for a denied
 * movement the way back: the handler's choice, else the region's settings of the flag, else the server's, else the
 * last accepted block, moved further back along the trail for a player who keeps being denied.
 */
public final class ProtectionFlagGate {

    /** The throttle key of an action no player is behind and no player suffers: one firing per interval for all of them. */
    static final UUID NO_PLAYER = new UUID(0, 0);

    private static final TriggerThrottle THROTTLE = new TriggerThrottle();
    private static final Duration DEFAULT_STUCK_WINDOW = Duration.ofSeconds(1);

    private ProtectionFlagGate() {
    }

    public static Decision decide(RegionLookup lookup, ActionContext context) {
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
        Resolution<T> resolution = lookup.resolution(flag, here, player);
        T value = resolution.getValue();
        if (value == null) {
            return false;
        }
        Region source = resolution.getSource();
        FlagSettings settings = settingsOf(flag, resolution, null);
        if (flag.decide(context, value, settings) == Decision.DENY) {
            fireApplied(flag, context, value, source, settings);
            FlagHandler handler = source == null ? null : source.getFlagHandler(flag);
            if (handler != null) {
                handler.handle(new FlagOutcomeContext(FlagOutcome.DENIED, player, source, flag, value, context.getSource(), context,
                        resolution.getReason(), 0));
            }
            return true;
        }
        if (source != null) {
            passed.add(() -> fireApplied(flag, context, value, source, settings));
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
        Resolution<T> resolution = lookup.explain(flag, crossed, player);
        T value = resolution.getValue();
        FlagSettings settings = settingsOf(flag, resolution, crossed);
        if (value == null || flag.decide(context, value, settings) != Decision.DENY) {
            if (crossed.hasFlagHandlers()) {
                notifyAllowed(lookup, flag, context, crossed, value, resolution);
            }
            return MoveVerdict.allowed();
        }
        Region source = resolution.getSource() != null ? resolution.getSource() : crossed;
        fireApplied(flag, context, value, source, settings);
        MoveDenySettings moveSettings = settings instanceof MoveDenySettings ? (MoveDenySettings) settings : null;
        int denials = session.noteDenial(flag.getKey(), RegionClock.now(), moveSettings != null ? moveSettings.getStuckWindow() : DEFAULT_STUCK_WINDOW);

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

    /**
     * Internal: whether a player found inside {@code context.getRegion()} without crossing its border is sent out - a
     * flag that would deny the entry and whose {@link EntryDenySettings} ask for it. Only that region's values decide.
     *
     * @return {@link MoveVerdict#allowed()} to stay, or where to put the player
     */
    public static MoveVerdict decideExpulsion(RegionLookup lookup, MoveContext context) {
        for (RegionFlag<?> flag : FlagRegistry.get().getBySource(EventSource.MOVE)) {
            if (flag.getCategory() == FlagCategory.PROTECTION && isAsked(flag, context)) {
                MoveVerdict verdict = expel(lookup, flag, context);
                if (!verdict.isAllowed()) {
                    return verdict;
                }
            }
        }
        return MoveVerdict.allowed();
    }

    private static <T> MoveVerdict expel(RegionLookup lookup, RegionFlag<T> flag, MoveContext context) {
        Region crossed = context.getRegion();
        Resolution<T> resolution = lookup.explain(flag, crossed, context.getPlayer());
        T value = resolution.getValue();
        FlagSettings settings = settingsOf(flag, resolution, crossed);
        if (!(settings instanceof EntryDenySettings) || !((EntryDenySettings) settings).isExpelFoundInside()
                || value == null || flag.decide(context, value, settings) != Decision.DENY) {
            return MoveVerdict.allowed();
        }
        EntryDenySettings entry = (EntryDenySettings) settings;
        //last-valid, trail and allow-after look back along a way the player never walked into this region
        WorldLocPos destination = destinationOf(entry.getRevert(), context);
        if (destination == null) {
            destination = entry.getStuckFallback();
        }
        if (destination == null) {
            destination = SpawnPoints.getServerSpawn(context.getPlayer(), context.getTo().getWorldName());
        }
        if (destination == null) {
            ECDebugModule.REGIONS.debug("{} was found inside {}, which expels them, but the platform knows no spawn in world {}; they stay.",
                    context.getPlayer().getName(), crossed.getId(), context.getTo().getWorldName());
            return MoveVerdict.allowed();
        }
        Region source = resolution.getSource() != null ? resolution.getSource() : crossed;
        fireApplied(flag, context, value, source, settings);
        return MoveVerdict.revert(destination);
    }

    /** Where a point strategy sends the player, read in the world the player is in; null when the platform does not know it. */
    private static @Nullable WorldLocPos destinationOf(RevertStrategy strategy, MoveContext context) {
        String world = context.getFrom().getWorldName();
        switch (strategy.getType()) {
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
        switch (strategy.getType()) {
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
        if (stuck != null && strategy.getType() != RevertStrategy.Type.ALLOW_AFTER && denials >= stuck.getStuckAfter()) {
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
        if (own.getFlag() != flag || own.getSource() == null || own.getValue() == null || flag.decide(context, own.getValue(), region.getEffectiveFlagSettings(flag)) != Decision.DENY) {
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
     * The settings in effect where the value came from: those the region gave the flag of the chain that defined the
     * value (an inherited {@code only=[...]} narrows the refinement too) when they fit the asked flag, else the asked
     * flag's own; {@code whereUndefined}'s (or the server's, when null) when no region gave the value.
     */
    private static <T> FlagSettings settingsOf(RegionFlag<T> asked, Resolution<T> resolution, @Nullable Region whereUndefined) {
        RegionFlag<T> definer = resolution.getFlag();
        RegionFlag<T> owner = asked.getSettingsType().isAssignableFrom(definer.getSettingsType()) ? definer : asked;
        Region source = resolution.getSource() != null ? resolution.getSource() : whereUndefined;
        return source != null ? source.getEffectiveFlagSettings(owner) : owner.getSettings();
    }

    /** @param settings those {@code decide} saw; the pace is read from the asked flag's own settings where the value came from */
    private static <T> void fireApplied(RegionFlag<T> flag, ActionContext context, T value, @Nullable Region source, FlagSettings settings) {
        Duration interval = source != null ? source.getEffectiveFlagSettings(flag).getTriggerInterval() : flag.getSettings().getTriggerInterval();
        if (THROTTLE.tryFire(paceKey(context), flag, interval)) {
            flag.onApplied(context, value, settings);
        }
    }

    /** Whose pace an effect counts against: the player behind the action, else the player it hurts, else {@link #NO_PLAYER}. */
    private static UUID paceKey(ActionContext context) {
        FPlayer player = context.getPlayer();
        if (player == null && context instanceof DamageContext) {
            player = ((DamageContext) context).getVictim().getRootPlayer();
        }
        return player == null ? NO_PLAYER : player.getUniqueId();
    }

    /** Internal: forgets the firing pace of a player who left. */
    public static void forget(UUID player) {
        THROTTLE.forget(player);
    }
}
