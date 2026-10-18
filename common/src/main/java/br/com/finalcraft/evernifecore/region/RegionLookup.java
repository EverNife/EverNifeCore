package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionIndex.WorldSnapshot;
import br.com.finalcraft.evernifecore.region.Resolution.Step;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.shape.ShapeType;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
 * <p>{@code resolve} answers a flag's effective value at a point for a subject: regions are taken in bands of
 * their current priority, highest first; a parent never competes in its own band - its children carry its values
 * down as defaults; inside a region, the highest-priority group that contains the subject and defines the flag
 * overrides the region's own value; distinct values tied in one band go to {@link RegionFlag#resolveTie}; nothing
 * defined gives {@link RegionFlag#getDefault()}. A subject holding {@code evernifecore.region.bypass.*} or
 * {@code evernifecore.region.bypass.<owner>.<name>} reads the default of every {@link RegionFlag#isBypassable()
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

    /**
     * The effective value of {@code flag} at the point for {@code subject}; a null subject skips groups and bypass.
     * Where nothing defines a refinement, it is the value of its nearest defined ancestor, as {@link #decide} reads it.
     */
    public <T> @Nullable T resolve(RegionFlag<T> flag, String world, double x, double y, double z, @Nullable FPlayer subject) {
        return resolution(flag, at(world, x, y, z), subject).getValue();
    }

    /** As the point overload, at the subject's current location. */
    public <T> @Nullable T resolve(RegionFlag<T> flag, FPlayer subject) {
        return resolution(flag, at(subject), subject).getValue();
    }

    /** The value {@code region} alone gives - its groups, its own value, its ancestors - ignoring the regions around it. */
    public <T> @Nullable T resolve(RegionFlag<T> flag, Region region, @Nullable FPlayer subject) {
        return climb(flag, asked -> own(asked, region, subject, null)).getValue();
    }

    /**
     * {@link #resolve(RegionFlag, Region, FPlayer)} step by step: the value {@code region} alone gives, the region of
     * its chain that gave it, and every step.
     */
    public <T> Resolution<T> explain(RegionFlag<T> flag, Region region, @Nullable FPlayer subject) {
        return climb(flag, asked -> own(asked, region, subject, new ArrayList<>()));
    }

    /** Internal: {@link #resolve} over regions already looked up, with the source and reason but no steps. */
    public <T> Resolution<T> resolution(RegionFlag<T> flag, List<Region> at, @Nullable FPlayer subject) {
        return climb(flag, asked -> walk(asked, at, subject, null));
    }

    /**
     * Whether the action is denied where it happens: the first protection flag that needs its source and denies
     * it wins, the flags after it are not asked. Each flag that took part runs its {@link RegionFlag#onApplied},
     * at most once per its trigger interval. A point outside every region passes without asking any flag. The native
     * handles of the context's actors are closed when it returns; an event asked in stages goes through one
     * {@link #dispatch()} instead. Thread: the thread of the event; it takes no lock.
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

    /** {@link #resolve} step by step: the same value, plus every region, group, rule and refined flag it went through. */
    public <T> Resolution<T> explain(RegionFlag<T> flag, String world, double x, double y, double z, @Nullable FPlayer subject) {
        List<Region> at = at(world, x, y, z);
        return climb(flag, asked -> walk(asked, at, subject, new ArrayList<>()));
    }

    /**
     * {@code own}'s answer for {@code flag}; where nothing defines it, the answer for its nearest registered ancestor
     * that is defined - an undefined refinement reads its parent's value - with the steps of the whole climb. A bypass
     * met on the way up stops the climb at {@code flag}'s default.
     */
    private static <T> Resolution<T> climb(RegionFlag<T> flag, Function<RegionFlag<T>, Resolution<T>> own) {
        Resolution<T> asked = own.apply(flag);
        if (asked.getReason() != Resolution.Reason.DEFAULT || flag.getParent() == null) {
            return asked;
        }
        List<Step> steps = new ArrayList<>(asked.getSteps());
        for (RegionFlag<T> ancestor = flag.getParent(); ancestor != null && ancestor.isRegistered(); ancestor = ancestor.getParent()) {
            Resolution<T> theirs = own.apply(ancestor);
            if (theirs.getReason() == Resolution.Reason.BYPASS) {
                break;
            }
            steps.addAll(theirs.getSteps());
            if (theirs.getReason() != Resolution.Reason.DEFAULT) {
                return new Resolution<>(ancestor, theirs.getValue(), theirs.getSource(), theirs.getReason(), steps);
            }
        }
        return asked;
    }

    /** The value {@code region} alone gives for {@code flag} itself, without climbing to the flag's ancestors. */
    private <T> Resolution<T> own(RegionFlag<T> flag, Region region, @Nullable FPlayer subject, @Nullable List<Step> steps) {
        List<Step> recorded = steps == null ? Collections.emptyList() : steps;
        if (bypasses(subject, flag, Collections.singletonList(region))) {
            step(steps, flag, null, 0, null, flag.getDefault(), Resolution.Reason.BYPASS);
            return new Resolution<>(flag, flag.getDefault(), null, Resolution.Reason.BYPASS, recorded);
        }
        Hit<T> hit = effective(region, region.getPriority(), flag, subject, steps);
        if (hit == null) {
            step(steps, flag, null, 0, null, flag.getDefault(), Resolution.Reason.DEFAULT);
            return new Resolution<>(flag, flag.getDefault(), null, Resolution.Reason.DEFAULT, recorded);
        }
        return new Resolution<>(flag, hit.value, hit.source, hit.reason, recorded);
    }

    private static final class Hit<T> {
        final T value;
        final Region source;
        final @Nullable AccessGroup group;
        final Resolution.Reason reason;

        Hit(T value, Region source, @Nullable AccessGroup group, Resolution.Reason reason) {
            this.value = value;
            this.source = source;
            this.group = group;
            this.reason = reason;
        }
    }

    /** @param steps where to record each step, or null to record nothing */
    private <T> Resolution<T> walk(RegionFlag<T> flag, List<Region> at, @Nullable FPlayer subject, @Nullable List<Step> steps) {
        List<Step> recorded = steps == null ? Collections.emptyList() : steps;
        if (bypasses(subject, flag, at)) {
            step(steps, flag, null, 0, null, flag.getDefault(), Resolution.Reason.BYPASS);
            return new Resolution<>(flag, flag.getDefault(), null, Resolution.Reason.BYPASS, recorded);
        }
        List<Region> candidates = new ArrayList<>(at.size());
        for (Region region : at) {
            if (isAncestorOfAny(region, at)) {
                step(steps, flag, region, region.getPriority(), null, null, Resolution.Reason.SKIPPED_ANCESTOR);
            } else {
                candidates.add(region);
            }
        }
        //bands by the live priority - the snapshot may predate a setPriority; the stable sort keeps its order inside a band
        candidates.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (int start = 0; start < candidates.size(); ) {
            int band = candidates.get(start).getPriority();
            List<T> values = new ArrayList<>(2);
            List<Hit<T>> hits = new ArrayList<>(2);
            int next = start;
            for (; next < candidates.size() && candidates.get(next).getPriority() == band; next++) {
                Region region = candidates.get(next);
                Hit<T> hit = effective(region, band, flag, subject, steps);
                if (hit == null) {
                    step(steps, flag, region, band, null, null, Resolution.Reason.NO_VALUE);
                } else {
                    values.add(hit.value);
                    hits.add(hit);
                }
            }
            if (!values.isEmpty()) {
                T value = pick(flag, values);
                if (!allEqual(values)) {
                    step(steps, flag, null, band, null, value, Resolution.Reason.TIE);
                }
                int index = values.indexOf(value);
                Hit<T> winner = index < 0 ? null : hits.get(index);
                return new Resolution<>(flag, value, winner == null ? null : winner.source, winner == null ? Resolution.Reason.TIE : winner.reason, recorded);
            }
            start = next;
        }
        step(steps, flag, null, 0, null, flag.getDefault(), Resolution.Reason.DEFAULT);
        return new Resolution<>(flag, flag.getDefault(), null, Resolution.Reason.DEFAULT, recorded);
    }

    /** The value {@code region} gives: its groups' for the subject, else its own, else its nearest ancestor's (groups included). */
    private <T> @Nullable Hit<T> effective(Region region, int band, RegionFlag<T> flag, @Nullable FPlayer subject, @Nullable List<Step> steps) {
        for (Region current = region; current != null; current = current.getParent()) {
            Hit<T> hit = subject == null ? null : groupValue(current, flag, subject);
            if (hit != null) {
                step(steps, flag, current, band, hit.group, hit.value, Resolution.Reason.GROUP_VALUE);
                return hit;
            }
            T local = current.getFlag(flag);
            if (local != null) {
                step(steps, flag, current, band, null, local, current == region ? Resolution.Reason.LOCAL_VALUE : Resolution.Reason.INHERITED);
                return new Hit<>(local, current, null, current == region ? Resolution.Reason.LOCAL_VALUE : Resolution.Reason.INHERITED);
            }
        }
        return null;
    }

    /** Among the groups of {@code region} that contain the subject and define the flag, the highest priority's value. */
    private static <T> @Nullable Hit<T> groupValue(Region region, RegionFlag<T> flag, FPlayer subject) {
        List<T> values = null;
        List<AccessGroup> groups = null;
        int best = Integer.MIN_VALUE;
        for (AccessGroup group : region.getGroups().values()) {
            T value = group.getFlag(flag);
            if (value == null || group.getPriority() < best || !group.contains(subject)) {
                continue;
            }
            if (values == null || group.getPriority() > best) {
                values = new ArrayList<>(2);
                groups = new ArrayList<>(2);
                best = group.getPriority();
            }
            values.add(value);
            groups.add(group);
        }
        if (values == null) {
            return null;
        }
        T value = pick(flag, values);
        int index = values.indexOf(value);
        return new Hit<>(value, region, index < 0 ? null : groups.get(index), Resolution.Reason.GROUP_VALUE);
    }

    private static <T> T pick(RegionFlag<T> flag, List<T> values) {
        return allEqual(values) ? values.get(0) : flag.resolveTie(values);
    }

    private static boolean allEqual(List<?> values) {
        for (int i = 1; i < values.size(); i++) {
            if (!values.get(i).equals(values.get(0))) {
                return false;
            }
        }
        return true;
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
    private static boolean bypasses(@Nullable FPlayer subject, RegionFlag<?> flag, List<Region> at) {
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

    private static void step(@Nullable List<Step> steps, RegionFlag<?> flag, @Nullable Region region, int priority,
                             @Nullable AccessGroup group, @Nullable Object value, Resolution.Reason reason) {
        if (steps != null) {
            steps.add(new Step(flag, region, priority, group, value, reason));
        }
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
        for (AccessGroup group : region.getGroups().values()) {
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

    void invalidate(AccessGroup group) {
        for (RegionPlayerSession session : sessions.values()) {
            session.invalidate(group);
        }
    }

    /** Drops the session of a player who left. */
    public void forgetSession(UUID player) {
        sessions.remove(player);
    }
}
