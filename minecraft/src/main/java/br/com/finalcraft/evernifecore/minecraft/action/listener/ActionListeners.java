package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageWatch;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionItems;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionListener;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Puts each action listener on the server exactly while its source is needed - by a region, or by a listener of its
 * action event: {@link EventSourceUsage} says when, a watch per source registers the listener on need and drops it once
 * idle. {@link #start} follows the core's sources; a plugin follows its own with {@link #follow}.
 */
public final class ActionListeners {

    /**
     * The core sources whose listener posts a cancellable {@code Pre}: all but those whose Bukkit event is no
     * {@code Cancellable} on any era - the respawn, the death, the furnace extraction and the experience change.
     */
    public static final Set<EventSource> PRE_SOURCES;

    static {
        Set<EventSource> pre = new HashSet<>(ActionEvents.all());
        pre.removeAll(Arrays.asList(EventSource.RESPAWN, EventSource.KILL, EventSource.SMELT, EventSource.XP_GAIN));
        PRE_SOURCES = Collections.unmodifiableSet(pre);
    }

    private final List<UsageWatch> watches = new ArrayList<>();
    private final List<ECListener> listeners = new ArrayList<>();

    private ActionListeners() {
    }

    /**
     * The core sources raised on this server, for {@code ActionSupport.declare}: null for every one, except before 1.12,
     * where breeding has no event and {@link EventSource#BREED} is left out.
     */
    public static @Nullable Set<EventSource> servedSources() {
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_12_R1)) {
            return null;
        }
        Set<EventSource> served = new HashSet<>();
        for (EventSource source : EventSource.all()) {
            if (source.getOwner().equals(CoreFlags.OWNER) && !source.equals(EventSource.BREED)) {
                served.add(source);
            }
        }
        return served;
    }

    /** How a message names this server: {@code Paper 1.21.1}. */
    public static String platformName() {
        String version = Bukkit.getBukkitVersion();
        int cut = version == null ? -1 : version.indexOf('-');
        return Bukkit.getName() + " " + (cut < 0 ? version : version.substring(0, cut));
    }

    /** Starts following every source, in the name of {@code owner}. */
    public static ActionListeners start(ECPluginData owner) {
        ActionListeners bridge = new ActionListeners();
        //on arrival everyone online is seeded where they stand, or the first step inside a deny-entry would be an entry
        bridge.watches.add(follow(owner, EventSource.MOVE, bridge.own(new MoveActionListener()), MoveActionListener::seedOnlinePlayers));
        bridge.watches.add(follow(owner, EventSource.BREAK, bridge.own(new BlockActionListener(EventSource.BREAK)), null));
        bridge.watches.add(follow(owner, EventSource.PLACE, bridge.own(new BlockActionListener(EventSource.PLACE)), null));
        bridge.watches.add(follow(owner, EventSource.INTERACT, bridge.own(new InteractActionListener()), null));
        //a second listener on the same source: the selection tool's clicks, kept on while a selection holds INTERACT
        bridge.watches.add(follow(owner, EventSource.INTERACT, bridge.own(new McSelectionListener(new McSelectionItems())), null));
        bridge.watches.add(follow(owner, EventSource.DAMAGE, bridge.own(new DamageActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.SPAWN, bridge.own(new SpawnActionListener()), null));
        //an explosion is asked as a whole (EXPLOSION) and then block by block (BREAK): deny-block-break alone holds a creeper
        List<EventSource> explosionStages = Arrays.asList(EventSource.EXPLOSION, EventSource.BREAK);
        bridge.watches.addAll(follow(owner, explosionStages, bridge.own(new ExplosionActionListener()), null));
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_8_R1)) {
            bridge.watches.addAll(follow(owner, explosionStages, bridge.own(new BlockExplodeActionListener()), null));
        }
        bridge.watches.add(follow(owner, EventSource.FLUID, bridge.own(new FluidActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.FIRE_SPREAD, bridge.own(new FireSpreadActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.RESPAWN, bridge.own(new RespawnActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.FLIGHT_TOGGLE, bridge.own(new FlightToggleActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.ITEM_DROP, bridge.own(new ItemActionListeners.Drop()), null));
        bridge.watches.add(follow(owner, EventSource.ITEM_PICKUP, bridge.own(MCVersion.isHigherEquals(MCDetailedVersion.v1_12_R1)
                ? new ItemActionListeners.Pickup() : new ItemActionListeners.LegacyPickup()), null));
        bridge.watches.add(follow(owner, EventSource.VEHICLE, bridge.own(new VehicleActionListeners.BreakAndEnter()), null));
        bridge.watches.add(follow(owner, EventSource.VEHICLE, bridge.own(MCVersion.isHigherEquals(MCDetailedVersion.v1_13_R1)
                ? new VehicleActionListeners.Place() : new VehicleActionListeners.LegacyPlace()), null));
        bridge.watches.add(follow(owner, EventSource.KILL, bridge.own(new KillActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.CRAFT, bridge.own(new CraftActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.SMELT, bridge.own(new SmeltActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.FISH, bridge.own(new FishActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.CONSUME, bridge.own(new ConsumeActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.XP_GAIN, bridge.own(new XpGainActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.ENCHANT, bridge.own(new EnchantActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.TAME, bridge.own(new TameActionListener()), null));
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_12_R1)) {   //EntityBreedEvent: breeding has no event before 1.12
            bridge.watches.add(follow(owner, EventSource.BREED, bridge.own(new BreedActionListener()), null));
        }
        bridge.watches.add(follow(owner, EventSource.SHEAR, bridge.own(new ShearActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.COMMAND, bridge.own(new CommandActionListener()), null));
        bridge.watches.add(follow(owner, EventSource.EXPLORE, bridge.own(new ExploreActionListener()), null));
        //ponytail: the journal hears every BREAK/PLACE/EXPLOSION while any region needs one, not only track-changes regions
        List<EventSource> terrainSources = Arrays.asList(EventSource.BREAK, EventSource.PLACE, EventSource.EXPLOSION);
        bridge.watches.addAll(follow(owner, terrainSources, bridge.own(new TerrainJournalListener()), null));
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_8_R1)) {
            bridge.watches.addAll(follow(owner, terrainSources, bridge.own(new TerrainJournalBlockExplodeListener()), null));
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
