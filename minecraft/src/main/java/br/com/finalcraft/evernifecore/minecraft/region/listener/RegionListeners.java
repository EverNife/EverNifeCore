package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionItems;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionListener;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Puts each region listener on the server exactly while its source is needed: {@link EventSourceUsage} says when, a
 * watch per source registers the listener on need and drops it once idle. {@link #start} follows the core's sources;
 * a plugin follows its own with {@link #follow}.
 */
public final class RegionListeners {

    private final List<UsageWatch> watches = new ArrayList<>();
    private final List<ECListener> listeners = new ArrayList<>();

    private RegionListeners() {
    }

    /** Starts following every source, in the name of {@code owner}. */
    public static RegionListeners start(ECPluginData owner) {
        RegionListeners bridge = new RegionListeners();
        //on arrival everyone online is seeded where they stand, or the first step inside a deny-entry would be an entry
        bridge.watches.add(follow(owner, EventSource.MOVE, bridge.own(new RegionMoveListener()), RegionMoveListener::seedOnlinePlayers));
        bridge.watches.add(follow(owner, EventSource.BREAK, bridge.own(new RegionBlockListener(EventSource.BREAK)), null));
        bridge.watches.add(follow(owner, EventSource.PLACE, bridge.own(new RegionBlockListener(EventSource.PLACE)), null));
        bridge.watches.add(follow(owner, EventSource.INTERACT, bridge.own(new RegionInteractListener()), null));
        //a second listener on the same source: the selection tool's clicks, kept on while a selection holds INTERACT
        bridge.watches.add(follow(owner, EventSource.INTERACT, bridge.own(new McSelectionListener(new McSelectionItems())), null));
        bridge.watches.add(follow(owner, EventSource.DAMAGE, bridge.own(new RegionDamageListener()), null));
        bridge.watches.add(follow(owner, EventSource.SPAWN, bridge.own(new RegionSpawnListener()), null));
        //an explosion is asked as a whole (EXPLOSION) and then block by block (BREAK): deny-block-break alone holds a creeper
        List<EventSource> explosionStages = Arrays.asList(EventSource.EXPLOSION, EventSource.BREAK);
        bridge.watches.addAll(follow(owner, explosionStages, bridge.own(new RegionExplosionListener()), null));
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_8_R1)) {
            bridge.watches.addAll(follow(owner, explosionStages, bridge.own(new RegionBlockExplodeListener()), null));
        }
        bridge.watches.add(follow(owner, EventSource.FLUID, bridge.own(new RegionFluidListener()), null));
        //ponytail: the journal hears every BREAK/PLACE/EXPLOSION while any region needs one, not only track-changes regions
        List<EventSource> terrainSources = Arrays.asList(EventSource.BREAK, EventSource.PLACE, EventSource.EXPLOSION);
        bridge.watches.addAll(follow(owner, terrainSources, bridge.own(new RegionTerrainListener()), null));
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_8_R1)) {
            bridge.watches.addAll(follow(owner, terrainSources, bridge.own(new RegionTerrainBlockExplodeListener()), null));
        }
        return bridge;
    }

    /**
     * Puts {@code listener} on the server exactly while some region needs {@code source} - registered in {@code owner}'s
     * name when the source becomes needed, dropped once it has stayed unneeded for the idle time - and runs
     * {@code afterRegistered} right after each registration. The watch returned stops the following; the listener itself
     * leaves with {@code owner} ({@link ECListener#unregisterAll}) or by {@code listener.unregisterThis()}.
     */
    public static UsageWatch follow(ECPluginData owner, EventSource source, ECListener listener, @Nullable Runnable afterRegistered) {
        return EventSourceUsage.get().watch(owner, source, () -> {
            if (ECListener.register(owner, listener) && afterRegistered != null) {
                afterRegistered.run();
            }
        }, listener::unregisterThis);
    }

    /**
     * As {@link #follow(ECPluginData, EventSource, ECListener, Runnable)}, for a listener that serves several sources: on
     * the server while any of {@code sources} is needed, dropped once every one has gone idle. Stop every watch returned
     * to stop the following.
     */
    public static List<UsageWatch> follow(ECPluginData owner, Collection<EventSource> sources, ECListener listener, @Nullable Runnable afterRegistered) {
        AtomicInteger needed = new AtomicInteger();
        List<UsageWatch> watches = new ArrayList<>(sources.size());
        for (EventSource source : sources) {
            watches.add(EventSourceUsage.get().watch(owner, source, () -> {
                if (needed.getAndIncrement() == 0 && ECListener.register(owner, listener) && afterRegistered != null) {
                    afterRegistered.run();
                }
            }, () -> {
                if (needed.decrementAndGet() == 0) {
                    listener.unregisterThis();
                }
            }));
        }
        return watches;
    }

    private ECListener own(ECListener listener) {
        listeners.add(listener);
        return listener;
    }

    /** No listener of this bridge is on the server after this, and none comes back. */
    public void stop() {
        watches.forEach(UsageWatch::stop);
        listeners.forEach(ECListener::unregisterThis);
    }
}
