package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionScheduler;
import br.com.finalcraft.evernifecore.region.Resolution;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.TickingFlag;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The region tick: one repeating task, every {@link #PERIOD_MILLIS}, scheduled only while some region needs
 * {@link EventSource#TICK}, that hands each {@link TickingFlag} the players standing in a region that holds it - every
 * other player is never looked at. Each world's players are ticked on that world's thread.
 */
public final class RegionTicker {

    public static final long PERIOD_MILLIS = 1000;

    private static final RegionTicker GLOBAL = new RegionTicker(RegionLookup.get());

    private final RegionLookup lookup;
    private final AtomicLong generation = new AtomicLong(); //each start bumps it; a tick of an older start is void
    private volatile boolean running;

    RegionTicker(RegionLookup lookup) {
        this.lookup = lookup;
    }

    public static RegionTicker get() {
        return GLOBAL;
    }

    /** Runs the global tick exactly while some region needs {@link EventSource#TICK}, in the name of {@code owner}. */
    public static UsageWatch start(ECPluginData owner) {
        return GLOBAL.follow(owner);
    }

    UsageWatch follow(ECPluginData owner) {
        return EventSourceUsage.get().watch(owner, EventSource.TICK, this::begin, this::end);
    }

    /** Whether the repeating task is scheduled now. */
    public boolean isRunning() {
        return running;
    }

    void begin() {
        long started = generation.incrementAndGet();
        running = true;
        RegionScheduler.scheduleAsync(() -> tick(started), PERIOD_MILLIS);
    }

    void end() {
        running = false;
        generation.incrementAndGet();
    }

    void tick(long started) {
        if (generation.get() != started) {
            return; //stopped, or restarted under a newer generation
        }
        Map<String, List<RegionPlayerSession>> byWorld = new HashMap<>();
        for (RegionPlayerSession session : lookup.getSessions()) {
            if (session.getPosition() != null && session.getOnlinePlayer() != null && holdsTickingFlag(session.getCurrent())) {
                byWorld.computeIfAbsent(session.getPosition().getWorldName(), world -> new ArrayList<>()).add(session);
            }
        }
        byWorld.forEach((world, sessions) -> {
            try {
                EverNifeCore.getPlatform().runOnWorldThread(world, () -> sessions.forEach(this::tickSession))
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                ECDebugModule.REGIONS.debug("Region tick in world '{}' skipped: {}", world, error.toString());
                            }
                        });
            } catch (RuntimeException refused) {
                ECDebugModule.REGIONS.debug("Region tick in world '{}' skipped: {}", world, refused.toString());
            }
        });
        RegionScheduler.scheduleAsync(() -> tick(started), PERIOD_MILLIS);
    }

    private static boolean holdsTickingFlag(List<Region> regions) {
        for (Region region : regions) {
            if (anyTicks(region.getFlags().keySet())) {
                return true;
            }
            for (AccessGroup group : region.getGroups().values()) {
                if (anyTicks(group.getFlags().keySet())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean anyTicks(Iterable<FlagKey> keys) {
        for (FlagKey key : keys) {
            RegionFlag<?> flag = FlagRegistry.get().get(key);
            if (flag instanceof TickingFlag && flag.getNeeds().contains(EventSource.TICK)) {
                return true;
            }
        }
        return false;
    }

    private void tickSession(RegionPlayerSession session) {
        FPlayer player = session.getOnlinePlayer();
        if (player == null || lookup.getSessionIfPresent(session.getPlayer()) != session) {
            return; //left between the collection and the world thread
        }
        for (RegionFlag<?> flag : FlagRegistry.get().getAll()) {
            if (flag instanceof TickingFlag) {
                tickFlag(player, session, flag);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void tickFlag(FPlayer player, RegionPlayerSession session, RegionFlag<T> flag) {
        Resolution<T> resolution = lookup.resolution(flag, session.getCurrent(), player);
        if (resolution.getSource() != null && resolution.getValue() != null) {
            ((TickingFlag<T>) flag).onTick(new SessionContext(player, resolution.getSource(), session), resolution.getValue());
        }
    }
}
