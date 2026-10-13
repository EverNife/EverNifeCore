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
