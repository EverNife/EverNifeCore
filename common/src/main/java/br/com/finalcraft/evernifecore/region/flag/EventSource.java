package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The kind of game event a flag needs to hear. EverNifeCore ships the nine every server has; a plugin registers the
 * ones only it raises with {@link #register}, and the region system treats them alike: a flag needs them, the count
 * says when they matter, a listener follows them. {@link #NONE} is for a flag that only stores a value.
 */
public final class EventSource {

    private static final ConcurrentHashMap<String, EventSource> REGISTERED = new ConcurrentHashMap<>();

    public static final EventSource MOVE = core("move");
    public static final EventSource BREAK = core("break");
    public static final EventSource PLACE = core("place");
    public static final EventSource INTERACT = core("interact");
    public static final EventSource DAMAGE = core("damage");
    public static final EventSource SPAWN = core("spawn");
    public static final EventSource EXPLOSION = core("explosion");
    public static final EventSource TICK = core("tick");
    public static final EventSource NONE = core("none");

    private final String owner;
    private final String name;
    private final String text;
    private final @Nullable ECPluginData plugin; //null for the core's own: they never leave

    private EventSource(String owner, String name, @Nullable ECPluginData plugin) {
        this.owner = owner;
        this.name = name;
        this.text = owner + ":" + name;
        this.plugin = plugin;
    }

    /**
     * {@code owner:name} for {@code owner}'s own events, both lowercased; the same name registered again is the same
     * source. A plugin registering {@code "move"} gets its own source, not {@link #MOVE}.
     *
     * @throws IllegalArgumentException when the plugin name or {@code name} breaks the {@link RegionId} rules
     */
    public static EventSource register(ECPluginData owner, String name) {
        String ownerName = RegionId.requireOwner(owner.getMetaInfo().getName());
        String sourceName = RegionId.requireName("event source", name);
        return REGISTERED.computeIfAbsent(ownerName + ":" + sourceName, text -> new EventSource(ownerName, sourceName, owner));
    }

    /** Forgets every source {@code owner} registered; the core's never leave. */
    public static void unregisterAll(ECPluginData owner) {
        REGISTERED.values().removeIf(source -> source.plugin == owner);
    }

    /** Every source registered now, the core's included. */
    public static Collection<EventSource> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTERED.values()));
    }

    private static EventSource core(String name) {
        return REGISTERED.computeIfAbsent(CoreFlags.OWNER + ":" + name, text -> new EventSource(CoreFlags.OWNER, name, null));
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EventSource && text.equals(((EventSource) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
