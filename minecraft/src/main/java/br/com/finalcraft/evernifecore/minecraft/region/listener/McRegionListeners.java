package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionItems;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionListener;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.region.usage.UsageWatch;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts each region listener on the server exactly while its source is needed: {@link EventSourceUsage} says when, a
 * watch per source registers the listener on need and drops it once idle.
 */
public final class McRegionListeners {

    private final ECPluginData owner;
    private final List<UsageWatch> watches = new ArrayList<>();
    private final List<ECListener> listeners = new ArrayList<>();

    private McRegionListeners(ECPluginData owner) {
        this.owner = owner;
    }

    /** Starts following every source, in the name of {@code owner}. */
    public static McRegionListeners start(ECPluginData owner) {
        McRegionListeners bridge = new McRegionListeners(owner);
        //on arrival everyone online is seeded where they stand, or the first step inside a deny-entry would be an entry
        bridge.follow(EventSource.MOVE, new RegionMoveListener(), RegionMoveListener::seedOnlinePlayers);
        bridge.follow(EventSource.BREAK, new RegionBlockListener(EventSource.BREAK), null);
        bridge.follow(EventSource.PLACE, new RegionBlockListener(EventSource.PLACE), null);
        bridge.follow(EventSource.INTERACT, new RegionInteractListener(), null);
        //a second listener on the same source: the selection tool's clicks, kept on while a selection holds INTERACT
        bridge.follow(EventSource.INTERACT, new McSelectionListener(new McSelectionItems()), null);
        bridge.follow(EventSource.DAMAGE, new RegionDamageListener(), null);
        bridge.follow(EventSource.SPAWN, new RegionSpawnListener(), null);
        return bridge;
    }

    private void follow(EventSource source, ECListener listener, @Nullable Runnable afterRegistered) {
        listeners.add(listener);
        watches.add(EventSourceUsage.get().watch(owner, source, () -> {
            if (ECListener.register(owner, listener) && afterRegistered != null) {
                afterRegistered.run();
            }
        }, listener::unregisterThis));
    }

    /** No listener of this bridge is on the server after this, and none comes back. */
    public void stop() {
        watches.forEach(UsageWatch::stop);
        listeners.forEach(ECListener::unregisterThis);
    }
}
