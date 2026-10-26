package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.ActorHolders;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagHolder;
import br.com.finalcraft.evernifecore.flag.FlagLayer;
import br.com.finalcraft.evernifecore.flag.FlagMode;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.flag.LayerRank;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.Resolution;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.TristateFlag;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionIndex.WorldSnapshot;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Read-only queries over every indexed region. Every method is safe from any thread and takes no lock: it reads
 * the world's current immutable snapshot, so a concurrent write is either fully visible or not at all.
 *
 * <p>{@code at}, {@code in} and {@code byWorld} come ordered by priority (highest first), then a child before its
 * parent, then id - the order as of the snapshot read, so a priority changed later shows once the region is
 * re-published. {@code byOwner}, {@code byTag} and {@code all} are unordered. Every returned collection is
 * read-only; a query that finds nothing returns the shared empty list.</p>
 *
 * <p>{@code resolve} answers a flag's effective value at a point for a subject by layers ({@link Resolution#getLayers()}):
 * every defined value of the candidate regions (a parent never competes beside its child - the child carries its values
 * down), of their groups that contain the subject, and of the holders that follow the subject ({@link ActorHolders}),
 * ordered by {@link LayerRank} - lock, written priority, then group in the region > region (by priority) > holder of the
 * player - and the newest first; cut after the first {@link FlagMode#SET}, and joined by the flag's
 * {@link FlagResolver} (the one the settings in effect name, when they name one). Nothing defined gives
 * {@link Flag#getDefault()}. A subject holding {@code evernifecore.region.bypass.*} or
 * {@code evernifecore.region.bypass.<owner>.<name>} reads the default of every {@link Flag#isBypassable()
 * bypassable} flag - both nodes are checked literally, since plain SuperPerms does not expand the wildcard - and so
 * does one holding the {@link FlagSettings#getBypassPermission() extra node} the flag's settings name where it is read.</p>
 */
public final class RegionLookup {

    private static final RegionLookup GLOBAL = new RegionLookup(new RegionIndex());

    private static final String BYPASS_ALL = "evernifecore.region.bypass.*";

    private final RegionIndex index;
    private final ConcurrentHashMap<UUID, RegionPlayerSession> sessions = new ConcurrentHashMap<>();

    RegionLookup(RegionIndex index) {
        this.index = index;
    }

    public static RegionLookup get() {
        return GLOBAL;
    }

    /** See {@link RegionIndex#setHugeChunks(int)}. */
    public void setHugeChunks(int hugeChunks) {
        index.setHugeChunks(hugeChunks);
    }

    /** The index this lookup reads; writers publish through it. */
    RegionIndex getIndex() {
        return index;
    }

    /** The regions containing the block at these coordinates. */
    public List<Region> at(String world, int x, int y, int z) {
        return at(index.snapshot(world), x, y, z);
    }

    /** The regions containing the point, judged by the block it is in. */
    public List<Region> at(String world, double x, double y, double z) {
        return at(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public List<Region> at(WorldBlockPos pos) {
        return at(pos.getWorldName(), pos.getX(), pos.getY(), pos.getZ());
    }

    public List<Region> at(WorldLocPos pos) {
        return at(pos.getWorldName(), pos.getX(), pos.getY(), pos.getZ());
    }

    public List<Region> at(FLocation location) {
        return at(location.getWorldLocPos());
    }

    /** The regions at the player's current location; none when the platform has no location for the player. */
    public List<Region> at(FPlayer player) {
        FLocation location = player.getLocation();
        return location == null ? Collections.emptyList() : at(location);
    }

    List<Region> at(WorldSnapshot snapshot, int x, int y, int z) {
        if (snapshot == WorldSnapshot.EMPTY) {
            return Collections.emptyList();
        }
        int shift = RegionGridOptions.getCurrent().getChunkShift();
        List<Region> found = collect(snapshot.buckets.get(RegionIndex.chunkKey(x >> shift, z >> shift)), x, y, z, null);
        found = collect(snapshot.huge, x, y, z, found);
        found = collect(snapshot.shapeless, x, y, z, found);
        return ordered(found, snapshot);
    }

    private static List<Region> collect(@Nullable Region[] regions, int x, int y, int z, @Nullable List<Region> found) {
        if (regions != null) {
            for (Region region : regions) {
                if (region.contains(x, y, z)) {
                    if (found == null) {
                        found = new ArrayList<>(4);
                    }
                    found.add(region);
                }
            }
        }
        return found;
    }

    /**
     * The regions of {@code world} that share a block with {@code shape}: exact when both are cuboids, by bounding
     * box otherwise - a polygon or a world-wide region may be reported although its area misses the shape.
     */
    public List<Region> in(String world, Shape shape) {
        WorldSnapshot snapshot = index.snapshot(world);
        if (snapshot == WorldSnapshot.EMPTY) {
            return Collections.emptyList();
        }
        Collection<Region> candidates;
        if (shape.getType() == ShapeType.WORLD || index.isHuge(shape)) {
            candidates = Arrays.asList(snapshot.all);
        } else {
            Set<Region> nearby = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ChunkPos chunk : shape.getChunks()) {
                Region[] bucket = snapshot.buckets.get(RegionIndex.chunkKey(chunk.getX(), chunk.getZ()));
                if (bucket != null) {
                    nearby.addAll(Arrays.asList(bucket));
                }
            }
            nearby.addAll(Arrays.asList(snapshot.huge));
            nearby.addAll(Arrays.asList(snapshot.shapeless));
            candidates = nearby;
        }
        List<Region> found = null;
        for (Region region : candidates) {
            if (region.getShape().intersects(shape)) {
                if (found == null) {
                    found = new ArrayList<>();
                }
                found.add(region);
            }
        }
        return ordered(found, snapshot);
    }

    public @Nullable Region byId(RegionId id) {
        return index.byId(id);
    }

    public Collection<Region> byOwner(ECPluginData owner) {
        List<Region> found = null;
        for (Region region : index.allRegions()) {
            if (region.getOwner() == owner) {
                found = add(found, region);
            }
        }
        return frozen(found);
    }

    public Collection<Region> byTag(String tag) {
        List<Region> found = null;
        for (Region region : index.allRegions()) {
            if (region.getTags().contains(tag)) {
                found = add(found, region);
            }
        }
        return frozen(found);
    }

    public Collection<Region> byWorld(String world) {
        Region[] all = index.snapshot(world).all;
        return all.length == 0 ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(all));
    }

    public Collection<Region> all() {
        return frozen(new ArrayList<>(index.allRegions()));
    }

    private static List<Region> add(@Nullable List<Region> found, Region region) {
        List<Region> list = found != null ? found : new ArrayList<>();
        list.add(region);
        return list;
    }

    private static List<Region> frozen(@Nullable List<Region> found) {
        return found == null || found.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /** Hits from the bucket, the huge and the shapeless arrays merged into the snapshot's order. */
    private static List<Region> ordered(@Nullable List<Region> found, WorldSnapshot snapshot) {
        if (found != null && found.size() > 1) {
            found.sort((a, b) -> Integer.compare(snapshot.rank.get(a), snapshot.rank.get(b)));
        }
        return frozen(found);
    }

    // ------------------------------------------------------------------
    //  Values
    // ------------------------------------------------------------------

    /** Layers by rank, then the newest first; the stable sort keeps the snapshot's order among full equals. */
    private static final Comparator<FlagLayer<?>> LAYER_ORDER = (a, b) -> {
        int compared = a.getRank().compareTo(b.getRank());
        return compared != 0 ? compared : b.getFlagValue().getSince().compareTo(a.getFlagValue().getSince());
    };

    /**
     * The effective value of {@code flag} at the point for {@code subject}; a null subject skips groups, the holders
     * that follow a player and bypass. Where nothing defines a refinement, it is the value of its nearest defined
     * ancestor, as {@link #decide} reads it. Thread: any; it takes no lock.
     */
    public <T> @Nullable T resolve(Flag<T> flag, String world, double x, double y, double z, @Nullable FPlayer subject) {
        return resolution(flag, at(world, x, y, z), subject).getValue();
    }

    /** As the point overload, at the subject's current location. Thread: any; it takes no lock. */
    public <T> @Nullable T resolve(Flag<T> flag, FPlayer subject) {
        return resolution(flag, at(subject), subject).getValue();
    }

    /**
     * The value {@code region} alone gives - its groups, its own value, its ancestors - ignoring the regions around it
     * and the holders that follow the subject. Thread: any; it takes no lock.
     */
    public <T> @Nullable T resolve(Flag<T> flag, Region region, @Nullable FPlayer subject) {
        return explain(flag, region, subject).getValue();
    }

    /** {@link #resolve(Flag, Region, FPlayer)} with its layers. Thread: any; it takes no lock. */
    public <T> Resolution<T> explain(Flag<T> flag, Region region, @Nullable FPlayer subject) {
        List<Region> alone = Collections.singletonList(region);
        return climb(flag, asked -> join(asked, alone, subject, false));
    }

    /**
     * {@link #resolve} over regions already looked up, with every layer: the candidate regions of {@code at} (an
     * ancestor of another candidate speaks through it), then the holders that follow {@code subject}
     * ({@link ActorHolders}). Thread: any; it takes no lock.
     */
    public <T> Resolution<T> resolution(Flag<T> flag, List<Region> at, @Nullable FPlayer subject) {
        return climb(flag, asked -> join(asked, at, subject, true));
    }

    /**
     * Whether the action is denied where it happens: the first protection flag that needs its source and denies
     * it wins, the flags after it are not asked. Each flag that took part runs its {@link Flag#onApplied},
     * at most once per its trigger interval. A point outside every region passes without asking any flag, unless a
     * holder that follows the player speaks. The native handles of the context's actors are closed when it returns; an
     * event asked in stages goes through one {@link #dispatch()} instead. Thread: the thread of the event; it takes no lock.
     */
    public Decision decide(ActionContext context) {
        try (RegionDispatch dispatch = dispatch()) {
            return dispatch.decide(context);
        }
    }

    /** Several decisions about one platform event, whose native handles stay open until the dispatch is closed. */
    public RegionDispatch dispatch() {
        return new RegionDispatch(this);
    }

    /** {@link #resolve} with {@link Tristate#UNDEFINED} for no value. */
    public Tristate tristate(TristateFlag flag, String world, double x, double y, double z, @Nullable FPlayer subject) {
        Tristate value = resolve(flag, world, x, y, z, subject);
        return value == null ? Tristate.UNDEFINED : value;
    }

    public boolean isTrue(TristateFlag flag, String world, double x, double y, double z, @Nullable FPlayer subject) {
        return tristate(flag, world, x, y, z, subject) == Tristate.TRUE;
    }

    /** {@link #resolve} with every layer it saw and what the resolver did with each. Thread: any; it takes no lock. */
    public <T> Resolution<T> explain(Flag<T> flag, String world, double x, double y, double z, @Nullable FPlayer subject) {
        return resolution(flag, at(world, x, y, z), subject);
    }

    /**
     * {@code own}'s answer for {@code flag}; where nothing defines it, the answer for its nearest registered ancestor
     * that is defined - an undefined refinement reads its parent's value. A bypass met on the way up stops the climb at
     * {@code flag}'s default.
     */
    private static <T> Resolution<T> climb(Flag<T> flag, Function<Flag<T>, Resolution<T>> own) {
        Resolution<T> asked = own.apply(flag);
        if (asked.getReason() != Resolution.Reason.DEFAULT || flag.getParent() == null) {
            return asked;
        }
        for (Flag<T> ancestor = flag.getParent(); ancestor != null && ancestor.isRegistered(); ancestor = ancestor.getParent()) {
            Resolution<T> theirs = own.apply(ancestor);
            if (theirs.getReason() == Resolution.Reason.BYPASS) {
                break;
            }
            if (theirs.getReason() != Resolution.Reason.DEFAULT) {
                return theirs;
            }
        }
        return asked;
    }

    /** Collects the layers of {@code flag} itself, without climbing to its ancestors, and joins them. */
    private <T> Resolution<T> join(Flag<T> flag, List<Region> at, @Nullable FPlayer subject, boolean withActors) {
        if (bypasses(subject, flag, at)) {
            return Resolution.ofDefault(flag, Resolution.Reason.BYPASS);
        }
        List<FlagLayer<T>> layers = new ArrayList<>(4);
        for (Region region : at) {
            if (!isAncestorOfAny(region, at)) {
                collect(flag, region, subject, layers);
            }
        }
        if (withActors && subject != null) {
            List<FlagHolder> holders = ActorHolders.holdersOf(subject);
            for (int i = 0; i < holders.size(); i++) {
                FlagHolder holder = holders.get(i);
                FlagValue<T> value = holder.accepts(flag) && flag.accepts(holder) ? holder.getFlagValue(flag) : null;
                if (value != null) {
                    layers.add(new FlagLayer<>(flag, holder, null, value, Resolution.Reason.ACTOR_VALUE, LayerRank.actor(value, i), FlagLayer.Use.COUNTED));
                }
            }
        }
        return layers.isEmpty() ? Resolution.ofDefault(flag, Resolution.Reason.DEFAULT) : joinLayers(flag, layers);
    }

    /**
     * The layers {@code region} gives: the nearest of it and its ancestors that defines anything for the subject - its
     * groups that contain the subject, and its own value. An undefined level reads the next one up (inherited).
     */
    private static <T> void collect(Flag<T> flag, Region region, @Nullable FPlayer subject, List<FlagLayer<T>> layers) {
        int band = region.getPriority();
        for (Region current = region; current != null; current = current.getParent()) {
            boolean found = false;
            if (subject != null) {
                for (Group group : current.getGroups().values()) {
                    FlagValue<T> value = group.getFlagValue(flag);
                    if (value != null && flag.accepts(group) && group.contains(subject)) {
                        layers.add(new FlagLayer<>(flag, group, current, value, Resolution.Reason.GROUP_VALUE,
                                LayerRank.place(value, band, group.getPriority()), FlagLayer.Use.COUNTED));
                        found = true;
                    }
                }
            }
            FlagValue<T> local = current.getFlagValue(flag);
            if (local != null && flag.accepts(current)) {
                layers.add(new FlagLayer<>(flag, current, current, local, current == region ? Resolution.Reason.LOCAL_VALUE : Resolution.Reason.INHERITED,
                        LayerRank.place(local, band, null), FlagLayer.Use.COUNTED));
                found = true;
            }
            if (found) {
                return;
            }
        }
    }

    /**
     * Orders the layers ({@link LayerRank}, then the newest), sets aside every lock that disagrees with the first lock,
     * cuts after the first {@link FlagMode#SET}, and joins the rest with the resolver the settings in effect name - those
     * of the region of the first layer that has one, else the server's.
     */
    private static <T> Resolution<T> joinLayers(Flag<T> flag, List<FlagLayer<T>> layers) {
        layers.sort(LAYER_ORDER);
        List<FlagLayer<T>> ordered = new ArrayList<>(layers.size());
        List<FlagLayer<T>> live = new ArrayList<>(layers.size());
        FlagLayer<T> firstLock = null;
        boolean cut = false;
        for (FlagLayer<T> layer : layers) {
            if (cut) {
                ordered.add(layer.withUse(FlagLayer.Use.CUT_BY_SET));
                continue;
            }
            if (layer.getFlagValue().isLock()) {
                if (firstLock == null) {
                    firstLock = layer;
                } else if (!firstLock.getValue().equals(layer.getValue())) {
                    ordered.add(layer.withUse(FlagLayer.Use.LOCK_OVERRULED));
                    continue;
                }
            }
            ordered.add(layer);
            live.add(layer);
            cut = layer.getFlagValue().getMode() == FlagMode.SET;
        }
        FlagSettings settings = flag.getSettings();
        for (FlagLayer<T> layer : live) {
            if (layer.getRegion() != null) {
                settings = layer.getRegion().getEffectiveFlagSettings(flag);
                break;
            }
        }
        FlagResolver<T> resolver = flag.getResolver(settings);
        FlagResolver.Resolved<T> resolved = resolver.resolve(live);
        Set<FlagLayer<T>> counted = Collections.newSetFromMap(new IdentityHashMap<>());
        counted.addAll(resolved.getCounted());
        for (int i = 0; i < ordered.size(); i++) {
            FlagLayer<T> layer = ordered.get(i);
            if (layer.getUse() == FlagLayer.Use.COUNTED && !counted.contains(layer)) {
                ordered.set(i, layer.withUse(FlagLayer.Use.NOT_COUNTED));
            }
        }
        return new Resolution<>(flag, resolved.getValue(), Resolution.Reason.DEFAULT, resolver, ordered);
    }

    private static boolean isAncestorOfAny(Region region, List<Region> regions) {
        for (Region other : regions) {
            for (Region ancestor = other.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
                if (ancestor == region) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The standard nodes, or the extra node the settings of {@code flag} name in any region of {@code at}. */
    private static boolean bypasses(@Nullable FPlayer subject, Flag<?> flag, List<Region> at) {
        if (subject == null || !flag.isBypassable()) {
            return false;
        }
        if (subject.hasPermission(BYPASS_ALL)
                || subject.hasPermission("evernifecore.region.bypass." + flag.getKey().getOwner() + "." + flag.getKey().getName())) {
            return true;
        }
        for (Region region : at) {
            String extra = region.getEffectiveFlagSettings(flag).getBypassPermission();
            if (extra != null && subject.hasPermission(extra)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The holders of other regions of {@code region}'s world that overlap it (by bounding box) and lock {@code flag} to
     * another value than a lock {@code region} (or one of its groups) holds - where they meet, the first lock in the
     * order wins and the other has no effect. Empty when {@code region} locks nothing for the flag.
     */
    public <T> List<FlagHolder> findLockConflicts(Flag<T> flag, Region region) {
        Set<T> mine = lockedValues(flag, region).keySet();
        if (mine.isEmpty()) {
            return Collections.emptyList();
        }
        List<FlagHolder> conflicts = new ArrayList<>(1);
        for (Region other : in(region.getWorld(), region.getShape())) {
            if (other == region) {
                continue;
            }
            for (Map.Entry<T, FlagHolder> theirs : lockedValues(flag, other).entrySet()) {
                if (!mine.contains(theirs.getKey()) || mine.size() > 1) {
                    conflicts.add(theirs.getValue());
                }
            }
        }
        return conflicts;
    }

    /** Every locked value of {@code flag} on {@code region} and its groups, with the holder that locks it. */
    static <T> Map<T, FlagHolder> lockedValues(Flag<T> flag, Region region) {
        Map<T, FlagHolder> locked = new LinkedHashMap<>(2);
        FlagValue<T> own = region.getFlagValue(flag);
        if (own != null && own.isLock()) {
            locked.put(own.getValue(), region);
        }
        for (Group group : region.getGroups().values()) {
            FlagValue<T> value = group.getFlagValue(flag);
            if (value != null && value.isLock()) {
                locked.putIfAbsent(value.getValue(), group);
            }
        }
        return locked;
    }

    // ------------------------------------------------------------------
    //  Sessions
    // ------------------------------------------------------------------

    /** The player's session, created on first use. */
    public RegionPlayerSession getSession(FPlayer player) {
        return sessions.computeIfAbsent(player.getUniqueId(), RegionPlayerSession::new);
    }

    public @Nullable RegionPlayerSession getSessionIfPresent(UUID player) {
        return sessions.get(player);
    }

    /** Every live session. Read-only. */
    public Collection<RegionPlayerSession> getSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Forgets every cached membership of {@code player} and has the session flags of the player looked at again -
     * for when something the owner tracks (a team, a rank) changed.
     */
    public void invalidate(UUID player) {
        RegionPlayerSession session = sessions.get(player);
        if (session != null) {
            session.invalidate();
            requestRevalidation(session);
        }
    }

    /**
     * Forgets the cached memberships of every group of {@code region}, in every session, and has the session flags
     * of every player inside it looked at again.
     */
    public void invalidate(Region region) {
        for (Group group : region.getGroups().values()) {
            invalidate(group);
        }
        for (RegionPlayerSession session : sessions.values()) {
            if (session.isAffectedBy(region)) {
                requestRevalidation(session);
            }
        }
    }

    private volatile @Nullable Consumer<RegionPlayerSession> revalidationListener;

    /** Internal: who runs a revalidation a session asked for; the movement tracker of this lookup. */
    public void setRevalidationListener(@Nullable Consumer<RegionPlayerSession> listener) {
        this.revalidationListener = listener;
    }

    /** Internal: marks every session flag of {@code session} due now and tells whoever runs them. */
    public void requestRevalidation(RegionPlayerSession session) {
        session.markRevalidateAll();
        Consumer<RegionPlayerSession> listener = revalidationListener;
        if (listener != null) {
            listener.accept(session);
        }
    }

    // ------------------------------------------------------------------
    //  Publication
    // ------------------------------------------------------------------

    /** Internal: runs {@code listener} with the world's name after every publish of a world's snapshot. */
    public void addPublishListener(Consumer<String> listener) {
        index.addPublishListener(listener);
    }

    public void removePublishListener(Consumer<String> listener) {
        index.removePublishListener(listener);
    }

    /** The version of {@code world}'s current snapshot; it grows with every publish. */
    public long getSnapshotVersion(String world) {
        return index.snapshot(world).version;
    }

    /** Internal: forgets the cached membership answers of {@code group}, in every session. */
    public void invalidate(Group group) {
        for (RegionPlayerSession session : sessions.values()) {
            session.invalidate(group);
        }
    }

    /** Drops the session of a player who left. */
    public void forgetSession(UUID player) {
        sessions.remove(player);
    }
}
