package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.FlagStore;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.GroupChange;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A named area of one world, owned by the plugin whose {@link RegionManager} created it. Not the {@code .mca}
 * region of a world save - that is {@link br.com.finalcraft.evernifecore.math.game.vector.region.RegionPos}.
 *
 * <p>Every setter may be called from any thread. A change marks the region for the owner's next write, re-indexes
 * it when lookups see the change, and posts the matching region event on the caller's thread. The shape changes
 * only through {@link RegionManager#redefine}.</p>
 *
 * <p>Equality is identity on purpose: two regions with the same id are two objects only while one replaces the
 * other. {@link #toString()} is the id.</p>
 */
public final class Region {

    private final RegionManager manager;
    private final RegionId id;
    private final String world;
    private final Persistence persistence;
    private volatile Shape shape;
    private volatile int priority;
    private volatile Region parent;
    private volatile @Nullable Instant expiresAt;
    private volatile JsonNode metadata;
    private volatile boolean attached;
    private final Set<String> tags = new CopyOnWriteArraySet<>();
    private final FlagStore flags = new FlagStore(this::onFlagChanged);
    private final ConcurrentHashMap<String, AccessGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<FlagKey, FlagSettings> flagSettings = new ConcurrentHashMap<>();
    /** Overrides of flags not registered, or not yet asked for: kept as stored, by {@code owner:name}. */
    private final ConcurrentHashMap<String, JsonNode> rawFlagSettings = new ConcurrentHashMap<>();

    /**
     * @param name the region name, see {@link RegionId#of}; the owner part of the id is the manager owner's plugin name
     * @throws IllegalArgumentException when the name breaks the rule, or the options' parent cannot be one
     */
    Region(RegionManager manager, String name, String world, Shape shape, RegionOptions options) {
        this.manager = manager;
        this.id = RegionId.of(manager.getOwner().getMetaInfo().getName(), name);
        this.world = world;
        this.shape = shape;
        this.persistence = options.getPersistence();
        this.priority = options.getPriority();
        this.expiresAt = options.getExpiresAt();
        this.metadata = options.getMetadata();
        this.tags.addAll(options.getTags());
        linkParent(options.getParent());
    }

    public RegionId getId() {
        return id;
    }

    public ECPluginData getOwner() {
        return manager.getOwner();
    }

    public RegionManager getManager() {
        return manager;
    }

    public String getWorld() {
        return world;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Shape getShape() {
        return shape;
    }

    /** Swaps the shape; the index sees it only once the region is re-published. */
    void setShape(Shape shape) {
        this.shape = shape;
    }

    /** Whether the region is live in its manager: indexed, evented and written. A removed region is not. */
    boolean isAttached() {
        return attached;
    }

    void setAttached(boolean attached) {
        this.attached = attached;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        manager.reprioritize(this, priority);
    }

    void setPriorityField(int priority) {
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
        manager.reparent(this, parent);
    }

    /** The validated assignment behind {@link #setParent}; the caller holds the write lock and re-publishes. */
    void linkParent(@Nullable Region parent) {
        if (parent != null) {
            if (parent.getOwner() != getOwner()) {
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

    /** Whether {@code ancestor} is this region's parent, or its parent's, and so on. */
    boolean descendsFrom(Region ancestor) {
        for (Region current = parent; current != null; current = current.parent) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** A read-only view of the tags. */
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void addTag(String tag) {
        if (tags.add(tag)) {
            manager.changed(this, RegionChangedEvent.Type.TAGS);
        }
    }

    public void removeTag(String tag) {
        if (tags.remove(tag)) {
            manager.changed(this, RegionChangedEvent.Type.TAGS);
        }
    }

    /** The owner's free-form data, as a copy: change it through {@link #setMetadata}. */
    public JsonNode getMetadata() {
        return metadata.deepCopy();
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata").deepCopy();
        manager.changed(this, RegionChangedEvent.Type.METADATA);
    }

    /** When the region removes itself (an {@code EXPIRED} removal); null for never. */
    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(@Nullable Instant expiresAt) {
        this.expiresAt = expiresAt;
        manager.changed(this, RegionChangedEvent.Type.EXPIRES_AT);
    }

    /** The online players standing inside, by the location the platform reports for each. */
    public List<FPlayer> getPlayersInside() {
        List<FPlayer> inside = new ArrayList<>();
        for (FPlayer player : EverNifeCore.getPlatform().getOnlinePlayers()) {
            FLocation location = player.getLocation();
            if (location != null && contains(location.getWorldLocPos())) {
                inside.add(player);
            }
        }
        return inside;
    }

    // ---- flags ----

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
        manager.flagChanged(this, null, flag, oldValue, newValue, cause);
    }

    // ---- flag settings ----

    /**
     * This region's own settings for {@code flag} - how the flag behaves here - or null when the region keeps the
     * server's. See {@link #getEffectiveFlagSettings}.
     */
    @SuppressWarnings("unchecked")
    public <S extends FlagSettings> @Nullable S getFlagSettings(RegionFlag<?> flag) {
        FlagSettings own = flagSettings.get(flag.getKey());
        if (own == null) {
            own = readRawSettings(flag);
        }
        return (S) own;
    }

    /** This region's own settings for {@code flag} when it has them, else the flag's server-wide ones. */
    @SuppressWarnings("unchecked")
    public <S extends FlagSettings> S getEffectiveFlagSettings(RegionFlag<?> flag) {
        FlagSettings own = getFlagSettings(flag);
        return own != null ? (S) own : flag.getSettings();
    }

    /**
     * Gives this region its own settings for {@code flag}. The object is kept as given: after changing it, set it
     * again so the change is written.
     *
     * @throws IllegalArgumentException when {@code settings} is not of the flag's {@link RegionFlag#getSettingsType()}
     */
    public void setFlagSettings(RegionFlag<?> flag, FlagSettings settings) {
        Class<? extends FlagSettings> expected = flag.getSettingsType();
        if (!expected.isInstance(settings)) {
            throw new IllegalArgumentException("Flag " + flag.getKey() + " takes settings of type " + expected.getName() + ", not "
                    + (settings == null ? "null" : settings.getClass().getName()) + " - build a " + expected.getSimpleName()
                    + " and pass that.");
        }
        flagSettings.put(flag.getKey(), settings);
        rawFlagSettings.remove(flag.getKey().toString());
        manager.changed(this, RegionChangedEvent.Type.FLAG_SETTINGS);
    }

    /** Goes back to the server's settings for {@code flag}; false when the region had none of its own. */
    public boolean removeFlagSettings(RegionFlag<?> flag) {
        boolean removed = flagSettings.remove(flag.getKey()) != null;
        removed |= rawFlagSettings.remove(flag.getKey().toString()) != null;
        if (removed) {
            manager.changed(this, RegionChangedEvent.Type.FLAG_SETTINGS);
        }
        return removed;
    }

    /** The overrides as storage writes them: every read one as a tree, plus the raw ones, by {@code owner:name}. */
    Map<String, JsonNode> getFlagSettingsForStorage() {
        Map<String, JsonNode> stored = new LinkedHashMap<>(rawFlagSettings);
        flagSettings.forEach((key, settings) -> stored.put(key.toString(),
                ConfigFactory.inMemoryCodec().getObjectMapper().valueToTree(settings)));
        return stored;
    }

    /** Takes a stored override as it was written; it is read into its settings type when first asked for. */
    void putRawFlagSettings(String key, JsonNode raw) {
        rawFlagSettings.put(key.toLowerCase(Locale.ROOT), raw);
    }

    private @Nullable FlagSettings readRawSettings(RegionFlag<?> flag) {
        String key = flag.getKey().toString();
        JsonNode raw = rawFlagSettings.get(key);
        if (raw == null) {
            return null;
        }
        try {
            FlagSettings read = ConfigFactory.inMemoryCodec().getObjectMapper().treeToValue(raw, flag.getSettingsType());
            FlagSettings raced = flagSettings.putIfAbsent(flag.getKey(), read);
            rawFlagSettings.remove(key, raw);
            return raced != null ? raced : read;
        } catch (JsonProcessingException | IllegalArgumentException unreadable) {
            getOwner().getLog().warning("Region {}: the stored settings of flag {} do not read as {} ({}). They are kept as stored and the"
                    + " server's settings apply - fix them in storage or set them again.", id, key, flag.getSettingsType().getSimpleName(),
                    unreadable.getMessage());
            return null;
        }
    }

    // ---- groups ----

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
        AccessGroup created = new AccessGroup(this, key, groupHook);
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
        manager.groupChanged(this, group, change, player);
    }

    private final AccessGroup.ChangeHook groupHook = new AccessGroup.ChangeHook() {
        @Override
        public void onGroupChanged(AccessGroup group, GroupChange change, @Nullable UUID player) {
            Region.this.onGroupChanged(group, change, player);
        }

        @Override
        public void onGroupFlagChanged(AccessGroup group, RegionFlag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue,
                                       FlagChangeCause cause) {
            manager.flagChanged(Region.this, group, flag, oldValue, newValue, cause);
        }
    };

    // ---- geometry ----

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
