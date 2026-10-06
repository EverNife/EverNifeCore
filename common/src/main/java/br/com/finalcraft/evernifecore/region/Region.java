package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagStore;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.GroupChange;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A named area of one world, owned by the plugin that created it. Not the {@code .mca} region of a world save -
 * that is {@link br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos}.
 *
 * <p>Equality is identity on purpose: two regions with the same id are two objects only while one replaces the
 * other. {@link #toString()} is the id.</p>
 */
public final class Region {

    private final RegionId id;
    private final ECPluginData owner;
    private final String world;
    private volatile Shape shape;
    private volatile int priority;
    private volatile Region parent;
    private final Set<String> tags = new CopyOnWriteArraySet<>();
    private final FlagStore flags = new FlagStore(this::onFlagChanged);
    private final ConcurrentHashMap<String, AccessGroup> groups = new ConcurrentHashMap<>();

    /**
     * @param name the region name, see {@link RegionId#of}; the owner part of the id is the owner's plugin name
     */
    Region(ECPluginData owner, String name, String world, Shape shape) {
        this.id = RegionId.of(owner.getMetaInfo().getName(), name);
        this.owner = owner;
        this.world = world;
        this.shape = shape;
    }

    public RegionId getId() {
        return id;
    }

    public ECPluginData getOwner() {
        return owner;
    }

    public String getWorld() {
        return world;
    }

    public Shape getShape() {
        return shape;
    }

    /** Swaps the shape; the index sees it only once the region is re-published. */
    void setShape(Shape shape) {
        this.shape = shape;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public @Nullable Region getParent() {
        return parent;
    }

    /**
     * @param parent a region of the same owner and world, or {@code null} for none
     * @throws IllegalArgumentException when the parent has another owner or world, or would close a cycle
     */
    public void setParent(@Nullable Region parent) {
        if (parent != null) {
            if (parent.owner != owner) {
                throw new IllegalArgumentException("Region " + id + " cannot have " + parent.id + " as parent: a parent must have the same owner"
                        + " (" + id.getOwner() + "), so pick one of " + id.getOwner() + "'s regions.");
            }
            if (!parent.world.equals(world)) {
                throw new IllegalArgumentException("Region " + id + " cannot have " + parent.id + " as parent: a parent must be in the same world"
                        + " ('" + world + "', not '" + parent.world + "').");
            }
            for (Region ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
                if (ancestor == this) {
                    throw new IllegalArgumentException("Region " + id + " cannot have " + parent.id + " as parent: " + parent.id
                            + " already descends from " + id + ", which would close a cycle - detach one of them first.");
                }
            }
        }
        this.parent = parent;
    }

    /** How many parents sit above this region; a child is always deeper than its parent. */
    int getDepth() {
        int depth = 0;
        for (Region ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            depth++;
        }
        return depth;
    }

    /** A read-only view of the tags. */
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void addTag(String tag) {
        tags.add(tag);
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    /** A read-only snapshot of the live local values: no group values, nothing from the parent. */
    public Map<FlagKey, FlagValue<?>> getFlags() {
        return flags.getFlags();
    }

    /**
     * The live local value of {@code flag}; null when unset or expired. Parents, groups and neighbours are not
     * consulted - resolving the effective value is {@link RegionLookup}'s job.
     */
    public <T> @Nullable T getFlag(RegionFlag<T> flag) {
        FlagValue<T> value = flags.getValue(flag);
        return value == null ? null : value.getValue();
    }

    public <T> @Nullable FlagValue<T> getFlagValue(RegionFlag<T> flag) {
        return flags.getValue(flag);
    }

    /**
     * Sets the permanent local value, dropping any temporary one over it.
     *
     * @throws IllegalStateException when {@code flag} is not registered
     */
    public <T> void setFlag(RegionFlag<T> flag, T value) {
        flags.set(flag, value);
    }

    /**
     * Sets a temporary local value until now + {@code ttl}; the permanent value underneath, if any, counts again
     * once it expires.
     *
     * @throws IllegalStateException when {@code flag} is not registered
     */
    public <T> void setFlag(RegionFlag<T> flag, T value, Duration ttl) {
        flags.set(flag, value, ttl);
    }

    /** Removes the local value, permanent and temporary; false when there was none. */
    public boolean removeFlag(RegionFlag<?> flag) {
        return flags.remove(flag);
    }

    /** The stored values of flags that are not registered, raw, by {@code owner:name}. Read-only. */
    public Map<String, JsonNode> getUnknownFlags() {
        return flags.getUnknown();
    }

    /** Internal: the store behind the flag methods, shared with the flag registry. */
    public FlagStore getFlagStore() {
        return flags;
    }

    /** Every change of a local flag value lands here, after the fact. */
    void onFlagChanged(RegionFlag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue, FlagChangeCause cause) {
        //nothing listens yet: the region manager wires the event bus and the storage dirty mark here
    }

    /** A read-only view of the groups, by name. */
    public Map<String, AccessGroup> getGroups() {
        return Collections.unmodifiableMap(groups);
    }

    /**
     * The group named {@code name}, created when absent. Names follow the region name rule and are lowercased.
     *
     * @throws IllegalArgumentException when the name breaks the rule; the message shows it and an example
     */
    public AccessGroup getGroup(String name) {
        String key = RegionId.requireName("group", name);
        AccessGroup existing = groups.get(key);
        if (existing != null) {
            return existing;
        }
        AccessGroup created = new AccessGroup(this, key, this::onGroupChanged);
        AccessGroup raced = groups.putIfAbsent(key, created);
        if (raced != null) {
            return raced;
        }
        onGroupChanged(created, GroupChange.CREATED, null);
        return created;
    }

    public @Nullable AccessGroup getGroupIfPresent(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    /** False when there was no such group. */
    public boolean removeGroup(String name) {
        AccessGroup removed = name == null ? null : groups.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        onGroupChanged(removed, GroupChange.REMOVED, null);
        return true;
    }

    /** Every change of a group lands here, after the fact; the cached memberships of the group go, in every session. */
    void onGroupChanged(AccessGroup group, GroupChange change, @Nullable UUID player) {
        RegionLookup.get().invalidate(group);
    }

    public boolean contains(int x, int y, int z) {
        return shape.contains(x, y, z);
    }

    public boolean contains(double x, double y, double z) {
        return shape.contains(x, y, z);
    }

    /** False when the position is in another world. */
    public boolean contains(WorldBlockPos pos) {
        return world.equals(pos.getWorldName()) && shape.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /** False when the position is in another world. */
    public boolean contains(WorldLocPos pos) {
        return world.equals(pos.getWorldName()) && shape.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
