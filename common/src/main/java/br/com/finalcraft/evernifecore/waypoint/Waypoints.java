package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import br.com.finalcraft.evernifecore.waypoint.channel.CompassWaypointChannel;
import br.com.finalcraft.evernifecore.waypoint.channel.ParticlePathWaypointChannel;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Points players at a target - a position, a region, something that moves - through every supported channel the
 * waypoint asks for: the core's compass and particle path, and whatever a platform or a plugin registers (holograms and
 * web maps on Bukkit). One ticker, every {@code Settings.Waypoint.refresh-ticks}, reads each target again on its
 * player's world thread and refreshes or, when the target is gone, hides. State lives in memory only: a waypoint does
 * not survive a restart, the plugin that showed it shows it again.
 */
public final class Waypoints {

    /** The bare names of the core's channels; {@code hologram} and {@code web-map} are Bukkit's. */
    public static final List<String> STOCK_CHANNELS = Collections.unmodifiableList(Arrays.asList("compass", "particle-path"));
    /** Every channel name the core or a platform of it registers - what {@code Settings.Waypoint.channels} may name. */
    public static final List<String> KNOWN_CHANNELS = Collections.unmodifiableList(Arrays.asList("compass", "particle-path", "hologram", "web-map"));

    private static final Waypoints INSTANCE = new Waypoints();

    private final TypeRegistry<WaypointChannel> registry = TypeRegistry.of(WaypointChannel.class, "waypoint-channel");
    //weak: a descriptor that left the registry (its plugin shut down) takes its channel with it
    private final Map<TypeDescriptor<WaypointChannel>, WaypointChannel> channels =
            Collections.synchronizedMap(new WeakHashMap<TypeDescriptor<WaypointChannel>, WaypointChannel>());
    private final List<WaypointChannel> stock = Collections.unmodifiableList(Arrays.<WaypointChannel>asList(
            new CompassWaypointChannel(), new ParticlePathWaypointChannel()));
    private final Map<UUID, Map<WaypointId, WaypointHandle>> active = new ConcurrentHashMap<>();
    private final AtomicBoolean ticking = new AtomicBoolean();
    //bumped by clearAll: a tick of the previous generation finds it changed and stops
    private final AtomicLong generation = new AtomicLong();
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private Waypoints() {
        ECEventBus.global().subscribe(ECPlayerQuitEvent.class, event -> onQuit(event.getPlayerData().getUniqueId()));
    }

    public static Waypoints get() {
        return INSTANCE;
    }

    /** The channels by key; {@link #register} is how a channel gets in. */
    public TypeRegistry<WaypointChannel> registry() {
        return registry;
    }

    /**
     * Registers the core's channels (compass, particle path) under {@code core}, the EverNifeCore plugin data.
     *
     * @throws IllegalStateException when they are registered already
     */
    public void registerStock(ECPluginData core) {
        register(core, stock.get(0), "Points the player's compass at the target; the compass item shows the way.");
        register(core, stock.get(1), "A line of particles from the player's eyes towards the target, redrawn every refresh.");
    }

    /**
     * Registers {@code channel} under {@code owner}, the plugin its key names; it leaves with {@code owner}'s shutdown.
     *
     * @throws IllegalStateException when the key is already registered
     */
    public void register(ECPluginData owner, WaypointChannel channel, String description) {
        TypeDescriptor<WaypointChannel> descriptor = TypeDescriptor.<WaypointChannel>of(channel.getKey())
                .describe(description)
                .factory(params -> channel)
                .build();
        channels.put(descriptor, channel);
        registry.register(owner, descriptor);
    }

    /** The channel {@code name} names: a bare name or an {@code owner:name} key; empty when none is registered. */
    public Optional<WaypointChannel> find(String name) {
        return registry.find(name).map(channels::get);
    }

    /** The bare names of the registered channels this platform supports, the list an error message teaches. */
    public List<String> getSupportedChannelNames() {
        List<String> names = new ArrayList<>();
        for (TypeDescriptor<WaypointChannel> descriptor : registry.getAll()) {
            WaypointChannel channel = channels.get(descriptor);
            if (channel != null && channel.isSupported()) {
                names.add(descriptor.getName());
            }
        }
        return names;
    }

    /** The waypoint's channels, else {@code Settings.Waypoint.channels}: the registered and supported ones, in order. */
    public List<WaypointChannel> resolve(Waypoint waypoint) {
        return resolve(channelNamesOf(waypoint));
    }

    private List<WaypointChannel> resolve(List<String> names) {
        List<WaypointChannel> supported = new ArrayList<>();
        for (String name : names) {
            WaypointChannel channel = find(name).orElse(null);
            if (channel != null && channel.isSupported() && !supported.contains(channel)) {
                supported.add(channel);
            }
        }
        return supported;
    }

    private static List<String> channelNamesOf(Waypoint waypoint) {
        return waypoint.getChannels() != null ? waypoint.getChannels() : ECSettings.WAYPOINT_CHANNELS;
    }

    /**
     * Shows {@code waypoint} to {@code player} through every supported channel it asks for, replacing one with the same
     * id. The channels draw on the player's world thread, after this returns. Thread: any.
     *
     * @throws WaypointException when none of its channels is supported here, naming them, this platform and the ones it has
     */
    public WaypointHandle show(FPlayer player, Waypoint waypoint) {
        List<String> names = channelNamesOf(waypoint);
        List<WaypointChannel> using = resolve(names);
        if (using.isEmpty()) {
            throw noSupportedChannel(waypoint.getId().getKey(), names);
        }
        Instant until = waypoint.getDuration() == null ? null : ECClock.now().plus(waypoint.getDuration());
        WaypointHandle handle = new WaypointHandle(player, waypoint, using, until);
        WaypointHandle previous = active.computeIfAbsent(player.getUniqueId(), uuid -> new ConcurrentHashMap<>())
                .put(waypoint.getId(), handle);
        if (previous != null) {
            previous.active = false;
        }
        onWorldThread(player, () -> {
            if (previous != null) {
                undraw(previous);
            }
            if (!handle.active) {
                return;
            }
            WorldLocPos at = locate(handle);
            if (at == null) {
                hide(handle);
                return;
            }
            handle.shown = true;
            for (WaypointChannel channel : handle.channels) {
                guarded(channel, () -> channel.show(player, waypoint, at));
            }
        });
        startTicker();
        return handle;
    }

    /** Hides the waypoint {@code id} of {@code player}; one never shown is a no-op. Thread: any. */
    public void hide(FPlayer player, WaypointId id) {
        Map<WaypointId, WaypointHandle> mine = active.get(player.getUniqueId());
        WaypointHandle handle = mine == null ? null : mine.get(id);
        if (handle != null) {
            hide(handle);
        }
    }

    /** Hides every waypoint of {@code player}. Thread: any. */
    public void hideAll(FPlayer player) {
        Map<WaypointId, WaypointHandle> mine = active.get(player.getUniqueId());
        if (mine != null) {
            for (WaypointHandle handle : new ArrayList<>(mine.values())) {
                hide(handle);
            }
        }
    }

    /** Hides, for every player, the waypoints whose id names {@code owner} - its shutdown. Thread: any. */
    public void hideOwnedBy(ECPluginData owner) {
        String name = owner.getMetaInfo().getName().toLowerCase(Locale.ROOT);
        for (Map<WaypointId, WaypointHandle> mine : active.values()) {
            for (WaypointHandle handle : new ArrayList<>(mine.values())) {
                if (handle.waypoint.getId().getOwner().equals(name)) {
                    hide(handle);
                }
            }
        }
    }

    /** What {@code player} is being pointed at now. */
    public Collection<Waypoint> getActive(FPlayer player) {
        Map<WaypointId, WaypointHandle> mine = active.get(player.getUniqueId());
        List<Waypoint> waypoints = new ArrayList<>();
        if (mine != null) {
            for (WaypointHandle handle : mine.values()) {
                waypoints.add(handle.waypoint);
            }
        }
        return waypoints;
    }

    /**
     * Fails when none of {@code channels} is supported on this platform - the check a plugin runs at load on every
     * waypoint it declares, so one that would point nowhere is found at boot, not by a player.
     *
     * @param what the waypoint's name for the message
     * @throws WaypointException naming the channels, this platform and the channels it supports
     */
    public void validate(ECPluginData caller, String what, List<String> channels) throws WaypointException {
        if (resolve(channels).isEmpty()) {
            throw noSupportedChannel(caller.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":" + what, channels);
        }
    }

    /** Forgets every waypoint without drawing anything and stops the ticker. Called when a test world closes. */
    public void clearAll() {
        generation.incrementAndGet();
        for (Map<WaypointId, WaypointHandle> mine : active.values()) {
            for (WaypointHandle handle : mine.values()) {
                handle.active = false;
            }
        }
        active.clear();
        ticking.set(false);
    }

    void hide(WaypointHandle handle) {
        if (!forget(handle)) {
            return;
        }
        onWorldThread(handle.player, () -> undraw(handle));
    }

    private WaypointException noSupportedChannel(String what, List<String> names) {
        return new WaypointException("The waypoint " + what + " asks for the channels " + names + ", and none of them is"
                + " supported on this platform (" + EverNifeCore.getPlatform().getPlatformProviderId() + "). Ask for one it has: "
                + getSupportedChannelNames() + ".");
    }

    private boolean forget(WaypointHandle handle) {
        Map<WaypointId, WaypointHandle> mine = active.get(handle.player.getUniqueId());
        boolean removed = mine != null && mine.remove(handle.waypoint.getId(), handle);
        handle.active = false;
        return removed;
    }

    private void undraw(WaypointHandle handle) {
        if (!handle.shown) {
            return;
        }
        handle.shown = false;
        for (WaypointChannel channel : handle.channels) {
            guarded(channel, () -> channel.hide(handle.player, handle.waypoint));
        }
    }

    private void onQuit(UUID player) {
        Map<WaypointId, WaypointHandle> mine = active.remove(player);
        if (mine == null) {
            return;
        }
        //the player is gone: no world thread to hop to, each channel just lets go of what it kept
        for (WaypointHandle handle : mine.values()) {
            handle.active = false;
            undraw(handle);
        }
    }

    private void startTicker() {
        if (ticking.compareAndSet(false, true)) {
            long current = generation.get();
            ECScheduler.scheduleAsync(() -> tick(current), refreshMillis());
        }
    }

    private void tick(long tickGeneration) {
        if (tickGeneration != generation.get()) {
            return;
        }
        Instant now = ECClock.now();
        for (Map<WaypointId, WaypointHandle> mine : active.values()) {
            for (WaypointHandle handle : new ArrayList<>(mine.values())) {
                if (!handle.player.isOnline()) {
                    forget(handle);
                } else if (handle.until != null && !now.isBefore(handle.until)) {
                    hide(handle);
                } else {
                    onWorldThread(handle.player, () -> refresh(handle));
                }
            }
        }
        active.values().removeIf(Map::isEmpty);
        ticking.set(false);
        //a show that raced the empty check above found the ticker still on and did not start one: start it here
        if (!active.isEmpty()) {
            startTicker();
        }
    }

    private void refresh(WaypointHandle handle) {
        if (!handle.active) {
            return;
        }
        WorldLocPos at = locate(handle);
        if (at == null) {
            hide(handle);
            return;
        }
        for (WaypointChannel channel : handle.channels) {
            guarded(channel, () -> channel.refresh(handle.player, handle.waypoint, at));
        }
    }

    private static @Nullable WorldLocPos locate(WaypointHandle handle) {
        return handle.waypoint.getTarget().locate();
    }

    private static long refreshMillis() {
        return Math.max(1, ECSettings.WAYPOINT_REFRESH_TICKS) * 50L;
    }

    //the player's own world thread; a player the platform cannot place yet is served on the main thread
    private static void onWorldThread(FPlayer player, Runnable task) {
        FLocation location = player.getLocation();
        if (location == null) {
            ECScheduler.onMainThread(task);
        } else {
            EverNifeCore.getPlatform().runOnWorldThread(location.getWorldName(), task);
        }
    }

    //a channel that throws costs its own drawing, never the ticker nor the other channels
    private void guarded(WaypointChannel channel, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException failure) {
            if (reportedFailures.add(channel.getKey())) {
                EverNifeCore.getLog().warning("The waypoint channel " + channel.getKey() + " failed and was skipped: " + failure);
            }
        }
    }
}
