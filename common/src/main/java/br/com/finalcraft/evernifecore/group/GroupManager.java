package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.group.event.GroupCreatedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupFlagChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupKindRegisteredEvent;
import br.com.finalcraft.evernifecore.group.storage.GroupRecord;
import br.com.finalcraft.evernifecore.group.storage.GroupStorage;
import br.com.finalcraft.evernifecore.logger.ECDebugModule;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionSweeper;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import br.com.finalcraft.evernifecore.util.retry.RetryPolicy;
import br.com.finalcraft.everydatabase.query.ScanRow;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The groups of one plugin: creates them, keeps them in the plugin's own {@link ECStorage} (collection
 * {@value GroupStorage#COLLECTION}) and posts a group event for every change. Kinds are registered before the load, so
 * every stored group reads as what it is.
 *
 * <pre>{@code
 * GroupManager groups = GroupManager.of(pluginData, storage);
 * groups.registerKind(PARTY);
 * groups.load(RetryPolicy.attempts(5).every(Duration.ofSeconds(2))).join();
 * Group alpha = groups.create("alpha", PARTY, GroupOptions.defaults().withLeader(leader));
 * }</pre>
 *
 * <p><b>Threads:</b> every method may be called from any thread. Writers are serialized by one lock of the manager,
 * reads never wait for it, and every event goes out on the thread of whoever made the change. Storage I/O never runs
 * on the caller's thread: changes are written in the background, a tick after they happen.</p>
 *
 * <p>The core closes the manager when its owner shuts down (writing what is pending first) but never the owner's
 * storage. A {@link Persistence#TRANSIENT TRANSIENT} group never reaches the storage and is dissolved
 * ({@link DissolveCause#OWNER_DISABLED}) when the manager closes.</p>
 */
public final class GroupManager implements GroupProvider {

    static final long DEFAULT_FLUSH_DELAY_MILLIS = 50;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 30_000;
    private static final long CLOSE_TIMEOUT_SECONDS = 30;

    private static final Map<ECPluginData, GroupManager> MANAGERS = new ConcurrentHashMap<>();

    private final ECPluginData owner;
    private final @Nullable ECStorage storage;
    private final @Nullable GroupStorage groupStorage;
    private final Object lock = new Object();
    private final Map<FlagKey, GroupKind> kinds = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    /** Stored groups whose kind is not registered: kept as read, never written, read again by {@link #registerKind}. */
    private final Map<String, GroupRecord> unread = new ConcurrentHashMap<>();
    private final Set<Group> dirty = ConcurrentHashMap.newKeySet();
    /** Names whose deletion the backend has not confirmed yet. */
    private final Set<String> removed = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final Object writeChain = new Object();
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private final Object loadLock = new Object();
    private @Nullable LoadRun run;
    private final CompletableFuture<Void> loadedOnce = new CompletableFuture<>();
    private volatile boolean loaded;
    private volatile boolean closed;
    private final Group.Host host = new ManagedHost();
    private final AtomicInteger ephemeralNames = new AtomicInteger();

    private GroupManager(ECPluginData owner, @Nullable ECStorage storage) {
        this.owner = owner;
        this.storage = storage;
        this.groupStorage = storage == null ? null : new GroupStorage(storage);
        if (storage == null) {
            loaded = true;
            loadedOnce.complete(null);
        }
    }

    /**
     * The manager of {@code owner} that writes nothing, created on first call: it keeps only TRANSIENT groups and refuses
     * to create a PERSISTENT one. When the owner already has a manager, on a storage or not, that one is returned. Any thread.
     */
    public static GroupManager of(ECPluginData owner) {
        Objects.requireNonNull(owner, "'owner' cannot be null when opening a GroupManager!");
        synchronized (MANAGERS) {
            return MANAGERS.computeIfAbsent(owner, key -> new GroupManager(key, null));
        }
    }

    /**
     * The manager of {@code owner} on {@code storage}, created on first call; later calls with the same storage return
     * the same manager. Any thread.
     *
     * @throws IllegalStateException when {@code owner} already has a manager without storage or on another storage, or
     *                               another plugin already keeps its groups in {@code storage}
     */
    public static GroupManager of(ECPluginData owner, ECStorage storage) {
        Objects.requireNonNull(owner, "'owner' cannot be null when opening a GroupManager!");
        Objects.requireNonNull(storage, "'storage' cannot be null when opening a GroupManager!");
        synchronized (MANAGERS) {
            GroupManager existing = MANAGERS.get(owner);
            if (existing != null) {
                if (existing.storage == storage) {
                    return existing;
                }
                throw new IllegalStateException(nameOf(owner) + " already has a GroupManager " + (existing.storage == null
                        ? "without storage (TRANSIENT groups only)" : "on another ECStorage") + " - close() it before opening one on this storage.");
            }
            for (GroupManager other : MANAGERS.values()) {
                if (other.storage == storage) {
                    throw new IllegalStateException("That ECStorage already keeps the groups of " + nameOf(other.owner) + ": every plugin"
                            + " keeps its groups in its own storage, so open a separate ECStorage for " + nameOf(owner) + ".");
                }
            }
            GroupManager created = new GroupManager(owner, storage);
            MANAGERS.put(owner, created);
            return created;
        }
    }

    /** The open manager of {@code owner}; null when it has none. Any thread. */
    public static @Nullable GroupManager ofIfPresent(ECPluginData owner) {
        return MANAGERS.get(owner);
    }

    /** Internal: every open manager. */
    public static Collection<GroupManager> all() {
        return Collections.unmodifiableCollection(MANAGERS.values());
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /** The owner's storage; null for a manager that writes nothing. */
    public @Nullable ECStorage getStorage() {
        return storage;
    }

    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------
    //  Kinds
    // ------------------------------------------------------------------

    /**
     * Registers {@code kind} on this manager. Call it before {@link #load()}: a stored group whose kind is not registered
     * stays unread (and untouched in storage) until its kind registers, and is read then.
     */
    public void registerKind(GroupKind kind) {
        Objects.requireNonNull(kind, "kind");
        kinds.put(kind.getKey(), kind);
        if (loaded) {
            String key = kind.getKey().toString();
            for (GroupRecord record : new ArrayList<>(unread.values())) {
                if (key.equalsIgnoreCase(record.kind) && unread.remove(record.name, record)) {
                    read(record, kind);
                }
            }
            wakeIfDue();
        }
        ECEventBus.global().postIfListened(GroupKindRegisteredEvent.class, () -> new GroupKindRegisteredEvent(this, kind));
    }

    /** The registered kinds. Read-only. */
    public Collection<GroupKind> getKinds() {
        return Collections.unmodifiableCollection(kinds.values());
    }

    // ------------------------------------------------------------------
    //  Load
    // ------------------------------------------------------------------

    /**
     * {@link #load(RetryPolicy)} that never gives up, retrying every 30 s. The future answers only the first try; the
     * retries go on in the background and {@link #whenLoaded()} tells when one of them succeeds.
     */
    public CompletableFuture<Void> load() {
        LoadRun current = startLoad(RetryPolicy.forever(Duration.ofMillis(DEFAULT_RETRY_DELAY_MILLIS)));
        return current == null ? CompletableFuture.completedFuture(null) : follow(current.firstTry);
    }

    /**
     * Reads every group of the owner, trying again as {@code policy} says. Any thread; the reads run in the background.
     * The future completes on the first try that succeeds, or fails once the policy gives up, naming the tries and the
     * last cause. A call while a load runs follows that load.
     */
    public CompletableFuture<Void> load(RetryPolicy policy) {
        Objects.requireNonNull(policy, "'policy' cannot be null - load() retries forever every 30 s.");
        LoadRun current = startLoad(policy);
        return current == null ? CompletableFuture.completedFuture(null) : follow(current.done);
    }

    /** True once a load succeeded; a manager that stores nothing starts loaded. */
    public boolean isLoaded() {
        return loaded;
    }

    /** Completes when the first load succeeds, and fails only when the manager closes before that. */
    public CompletableFuture<Void> whenLoaded() {
        return follow(loadedOnce);
    }

    private @Nullable LoadRun startLoad(RetryPolicy policy) {
        synchronized (loadLock) {
            if (groupStorage == null || closed) {
                return null;
            }
            if (run == null || run.done.isDone()) {
                run = new LoadRun(policy);
                LoadRun started = run;
                ECScheduler.scheduleAsync(() -> attempt(started), 0);
            }
            return run;
        }
    }

    private void attempt(LoadRun current) {
        synchronized (loadLock) {
            if (closed || current.done.isDone()) {
                return;
            }
            current.attempts++;
        }
        CompletableFuture<List<ScanRow<GroupRecord>>> read;
        try {
            read = groupStorage.loadAll();
        } catch (RuntimeException failure) {
            read = new CompletableFuture<>();
            read.completeExceptionally(failure);
        }
        read.whenComplete((rows, error) -> {
            if (error == null) {
                try {
                    publishLoaded(rows);
                    loaded = true;
                    loadedOnce.complete(null);
                    current.firstTry.complete(null);
                    current.done.complete(null);
                    return;
                } catch (RuntimeException failure) {
                    error = failure;
                }
            }
            onLoadFailed(current, error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
        });
    }

    private void onLoadFailed(LoadRun current, Throwable error) {
        current.firstTry.completeExceptionally(error);
        Duration elapsed = Duration.between(current.startedAt, ECClock.now());
        Duration retryIn = closed ? null : current.policy.delayBefore(current.attempts + 1, elapsed);
        if (retryIn != null) {
            owner.getLog().warning("Groups of {} are not loaded ({}); try {} failed, the next one runs in {} s.", nameOf(owner),
                    error.toString(), current.attempts, retryIn.toMillis() / 1000.0);
            ECScheduler.scheduleAsync(() -> attempt(current), retryIn.toMillis());
            return;
        }
        String gaveUp = "Groups of " + nameOf(owner) + " did not load after " + current.attempts + (current.attempts == 1 ? " try" : " tries")
                + " (" + current.policy + "); last cause: " + error + ". Nothing retries on its own - call load again once the storage"
                + " is reachable.";
        if (!closed) {
            owner.getLog().warning(gaveUp);
        }
        current.done.completeExceptionally(new IllegalStateException(gaveUp, error));
    }

    private void publishLoaded(List<ScanRow<GroupRecord>> rows) {
        int read = 0;
        for (ScanRow<GroupRecord> row : rows) {
            if (row.isFailed()) {
                owner.getLog().warning("Group '{}' of {} does not read ({}); it is skipped and stays in storage untouched.", row.key(),
                        nameOf(owner), String.valueOf(row.error()));
                continue;
            }
            GroupRecord record = row.value();
            GroupKind kind = record.kind == null ? null : kindOf(record.kind);
            if (kind == null) {
                unread.put(record.name, record);
                owner.getLog().warning("group '{}' of {} has kind '{}', which is not registered: call manager.registerKind(...) before load()"
                        + " or the group stays unread", record.name, nameOf(owner), record.kind);
                continue;
            }
            if (read(record, kind)) {
                read++;
            }
        }
        ECDebugModule.GROUPS.debug("{} loaded {} group(s); {} wait for their kind.", nameOf(owner), read, unread.size());
        wakeIfDue();
    }

    private @Nullable GroupKind kindOf(String key) {
        try {
            return kinds.get(FlagKey.parse(key));
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /** Builds the group of {@code record} and puts it in place; false (and a warning) when the record does not read. */
    private boolean read(GroupRecord record, GroupKind kind) {
        try {
            Group group = new Group(GroupId.of(owner, record.name), kind, owner, Persistence.PERSISTENT, null,
                    record.createdAt == null ? Instant.EPOCH : record.createdAt, host);
            group.restore(record.priority, record.permission, MembershipCheck.parse(record.check), record.displayName, record.metadata);
            record.members.forEach((uuid, member) -> group.restoreMember(UUID.fromString(uuid), member.joinedAt, member.expiresAt, member.roles));
            record.invites.forEach((uuid, invite) -> group.restoreInvite(UUID.fromString(uuid), invite.invitedAt, invite.expiresAt,
                    invite.invitedBy == null ? null : UUID.fromString(invite.invitedBy)));
            group.restoreLeader(record.leader == null ? null : UUID.fromString(record.leader));
            record.flags.forEach(group.getFlagStore()::putRaw);
            groups.put(group.getName(), group);
            return true;
        } catch (RuntimeException unreadable) {
            owner.getLog().warning("Group '{}' of {} does not read ({}); it is skipped and stays in storage untouched.", record.name,
                    nameOf(owner), unreadable.getMessage());
            return false;
        }
    }

    private static GroupRecord toRecord(Group group) {
        GroupRecord record = new GroupRecord();
        record.name = group.getName();
        record.kind = group.getKind().getKey().toString();
        record.leader = group.getLeader() == null ? null : group.getLeader().toString();
        record.displayName = group.getDisplayName();
        record.createdAt = group.getCreatedAt();
        for (GroupMember member : group.getStoredMembers()) {
            List<String> roles = new ArrayList<>();
            for (GroupRole role : member.getRoles()) {
                roles.add(role.getName());
            }
            roles.addAll(member.getUnknownRoles());
            record.members.put(member.getUuid().toString(), new GroupRecord.MemberRecord(member.getJoinedAt(), member.getExpiresAt(), roles));
        }
        for (Invite invite : group.getStoredInvites()) {
            record.invites.put(invite.getPlayer().toString(), new GroupRecord.InviteRecord(invite.getInvitedAt(), invite.getExpiresAt(),
                    invite.getInvitedBy() == null ? null : invite.getInvitedBy().toString()));
        }
        record.flags.putAll(group.getFlagStore().toRaw());
        record.metadata = group.getMetadata();
        record.permission = group.getPermission();
        record.check = group.getMembershipCheck().toText();
        record.priority = group.getPriority();
        return record;
    }

    // ------------------------------------------------------------------
    //  Groups
    // ------------------------------------------------------------------

    /** {@link #create(String, GroupKind, GroupOptions)} with the defaults: PERSISTENT when the manager has a storage. */
    public Group create(String name, GroupKind kind) {
        return create(name, kind, GroupOptions.defaults());
    }

    /**
     * Creates group {@code name} of {@code kind} and posts {@link GroupCreatedEvent}; a leader in the options joins as the
     * first member (by {@link Group#add}) and leads.
     *
     * @throws IllegalArgumentException when the name breaks the region name rule
     * @throws IllegalStateException    when the manager is not loaded or closed, the name is taken, the kind is not
     *                                  registered, or a PERSISTENT group is asked of a manager without storage
     */
    public Group create(String name, GroupKind kind, GroupOptions options) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(options, "options");
        String key = RegionId.requireName("group", name);
        Group group;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("The GroupManager of " + nameOf(owner) + " is closed: GroupManager.of(owner, storage) opens a"
                        + " new one.");
            }
            if (!loaded) {
                throw new IllegalStateException("The GroupManager of " + nameOf(owner) + " has not loaded its groups yet, so '" + key
                        + "' could be one of them: create once load() completes (whenLoaded()).");
            }
            if (!kind.equals(kinds.get(kind.getKey()))) {
                throw new IllegalStateException("Group kind " + kind.getKey() + " is not registered on the GroupManager of " + nameOf(owner)
                        + ": call registerKind(kind) before load(), then create.");
            }
            if (groups.containsKey(key) || unread.containsKey(key)) {
                throw new IllegalStateException(nameOf(owner) + " already has a group named '" + key + "': get(\"" + key + "\") returns it,"
                        + " or pick another name.");
            }
            Persistence persistence = options.getPersistence() != null ? options.getPersistence()
                    : storage != null ? Persistence.PERSISTENT : Persistence.TRANSIENT;
            if (persistence == Persistence.PERSISTENT && storage == null) {
                throw new IllegalStateException(nameOf(owner) + " opened its GroupManager without a storage, so it can only hold TRANSIENT"
                        + " groups. Open it with GroupManager.of(owner, storage) to persist '" + key + "'.");
            }
            group = new Group(GroupId.of(owner, key), kind, owner, persistence, null, ECClock.now(), host);
            group.restore(0, null, MembershipCheck.exact(), options.getDisplayName(), options.getMetadata());
            groups.put(key, group);
        }
        group.setJoinEdge(options.getJoinEdge());
        group.setLeaveEdge(options.getLeaveEdge());
        if (options.getKickWhen() != null) {
            group.setKickWhen(options.getKickWhen(), options.getKickReason());
        }
        if (options.getQuitPolicy() != null) {
            group.setQuitPolicy(options.getQuitPolicy());
        }
        markDirty(group);
        ECEventBus.global().postIfListened(GroupCreatedEvent.class, () -> new GroupCreatedEvent(group));
        UUID leader = options.getLeader();
        if (leader != null && group.add(leader)) {
            group.setLeader(leader);
        }
        return group;
    }

    /** Null when the owner has no group named {@code name}. */
    public @Nullable Group get(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    /** Every group. Read-only snapshot. */
    public Collection<Group> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(groups.values()));
    }

    public Collection<Group> getByKind(GroupKind kind) {
        List<Group> found = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.getKind().equals(kind)) {
                found.add(group);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** The groups {@code player} is an explicit member of. */
    public List<Group> getGroupsOf(UUID player) {
        List<Group> found = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.isMember(player)) {
                found.add(group);
            }
        }
        return Collections.unmodifiableList(found);
    }

    public List<Group> getGroupsOf(UUID player, GroupKind kind) {
        List<Group> found = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.getKind().equals(kind) && group.isMember(player)) {
                found.add(group);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * The one group of a ONE kind {@code player} is in; null when none.
     *
     * @throws IllegalStateException when the kind is MANY - use {@link #getGroupsOf(UUID, GroupKind)}
     */
    public @Nullable Group getGroupOf(UUID player, GroupKind kind) {
        if (kind.getCardinality() != GroupKind.Cardinality.ONE) {
            throw new IllegalStateException("Group kind " + kind.getKey() + " is MANY, so a player may be in several of its groups:"
                    + " ask getGroupsOf(player, kind).");
        }
        List<Group> found = getGroupsOf(player, kind);
        return found.isEmpty() ? null : found.get(0);
    }

    /** The groups whose {@link Group#getScope() scope} starts with {@code prefix}. */
    public List<Group> getByScope(String prefix) {
        List<Group> found = new ArrayList<>();
        for (Group group : groups.values()) {
            String scope = group.getScope();
            if (scope != null && scope.startsWith(prefix)) {
                found.add(group);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** A free name for an ephemeral group: {@code e<n>}. */
    String nextEphemeralName() {
        String name;
        do {
            name = "e" + ephemeralNames.incrementAndGet();
        } while (groups.containsKey(name) || unread.containsKey(name));
        return name;
    }

    // ------------------------------------------------------------------
    //  GroupProvider
    // ------------------------------------------------------------------

    @Override
    public List<GroupId> getGroupIdsOf(UUID player) {
        return idsOf(getGroupsOf(player));
    }

    @Override
    public List<GroupId> getGroupIdsOf(UUID player, GroupKind kind) {
        return idsOf(getGroupsOf(player, kind));
    }

    @Override
    public boolean exists(GroupId id) {
        return byId(id) != null;
    }

    @Override
    public Set<UUID> getMembers(GroupId id) {
        Group group = byId(id);
        if (group == null) {
            return Collections.emptySet();
        }
        Set<UUID> uuids = new LinkedHashSet<>();
        for (GroupMember member : group.getMembers()) {
            uuids.add(member.getUuid());
        }
        return Collections.unmodifiableSet(uuids);
    }

    @Override
    public @Nullable UUID getLeader(GroupId id) {
        Group group = byId(id);
        return group == null ? null : group.getLeader();
    }

    /** The groups that hold {@code player} ({@link Group#contains}): the ACTOR layer of the flag resolution. */
    @Override
    public List<Group> getHoldersOf(FPlayer player) {
        List<Group> found = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.contains(player)) {
                found.add(group);
            }
        }
        return found;
    }

    /** This manager's group {@code id} names; null for another owner's, a region-local one or an unknown name. */
    public @Nullable Group byId(GroupId id) {
        if (id.isRegionLocal() || !id.getOwner().equals(GroupId.of(owner, "x").getOwner())) {
            return null;
        }
        return get(id.getName());
    }

    private static List<GroupId> idsOf(List<Group> found) {
        List<GroupId> ids = new ArrayList<>(found.size());
        for (Group group : found) {
            ids.add(group.getId());
        }
        return Collections.unmodifiableList(ids);
    }

    // ------------------------------------------------------------------
    //  Writes
    // ------------------------------------------------------------------

    private void markDirty(Group group) {
        if (group.getPersistence() == Persistence.TRANSIENT || groupStorage == null || closed || groups.get(group.getName()) != group) {
            return;
        }
        dirty.add(group);
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (groupStorage != null && flushScheduled.compareAndSet(false, true)) {
            ECScheduler.scheduleAsync(this::write, DEFAULT_FLUSH_DELAY_MILLIS);
        }
    }

    /** Writes every pending change and deletion; completes once the backend confirmed them all. Any thread. */
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        ECScheduler.scheduleAsync(() -> write().whenComplete((ignored, error) -> {
            if (error == null) {
                done.complete(null);
            } else {
                done.completeExceptionally(error);
            }
        }), 0);
        return done;
    }

    /** Runs after every write already started, so an older state of a group never lands after a newer one. */
    private CompletableFuture<Void> write() {
        synchronized (writeChain) {
            lastWrite = lastWrite.handle((ignored, error) -> null).thenCompose(ignored -> writeNow());
            return lastWrite;
        }
    }

    private CompletableFuture<Void> writeNow() {
        flushScheduled.set(false);
        if (groupStorage == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Throwable>> pending = new ArrayList<>();
        for (String name : new ArrayList<>(removed)) {
            pending.add(groupStorage.delete(name).handle((ignored, error) -> {
                if (error == null) {
                    removed.remove(name);
                } else {
                    owner.getLog().warning("Group '{}' of {} could not be deleted from storage ({}); the next write tries again.", name,
                            nameOf(owner), error.toString());
                }
                return error;
            }));
        }
        for (Group group : new ArrayList<>(dirty)) {
            if (!dirty.remove(group)) {
                continue;
            }
            pending.add(groupStorage.save(toRecord(group)).handle((ignored, error) -> {
                if (error != null) {
                    if (groups.get(group.getName()) == group) {
                        dirty.add(group);
                    }
                    owner.getLog().warning("Group {} could not be written to storage ({}); the next write tries again.", group.getId(),
                            error.toString());
                }
                return error;
            }));
        }
        int writes = pending.size();
        return CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            ECDebugModule.GROUPS.debug("{} wrote or deleted {} group(s).", nameOf(owner), writes);
            for (CompletableFuture<Throwable> future : pending) {
                Throwable error = future.join();
                if (error != null) {
                    throw new CompletionException(error);
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    /**
     * Dissolves the TRANSIENT groups ({@link DissolveCause#OWNER_DISABLED}), writes what is pending and forgets the
     * manager. The storage stays open. Any thread; the core calls it when the owner shuts down.
     */
    public CompletableFuture<Void> close() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        dissolveTransient();
        synchronized (MANAGERS) {
            closed = true;
            MANAGERS.remove(owner, this);
        }
        failPendingLoads();
        ECDebugModule.GROUPS.debug("{} closed its GroupManager: {} group(s) dropped.", nameOf(owner), groups.size());
        for (Group group : groups.values()) {
            group.detach();
        }
        return flush().whenComplete((ignored, error) -> groups.clear());
    }

    private void dissolveTransient() {
        for (Group group : new ArrayList<>(groups.values())) {
            if (group.getPersistence() == Persistence.TRANSIENT) {
                group.dissolve(DissolveCause.OWNER_DISABLED);
            }
        }
    }

    private void failPendingLoads() {
        IllegalStateException closedEarly = new IllegalStateException("The GroupManager of " + nameOf(owner) + " was closed before its"
                + " groups loaded - GroupManager.of(owner, storage) opens a new one to load them.");
        LoadRun current;
        synchronized (loadLock) {
            current = run;
        }
        if (current != null) {
            current.firstTry.completeExceptionally(closedEarly);
            current.done.completeExceptionally(closedEarly);
        }
        loadedOnce.completeExceptionally(closedEarly);
    }

    /**
     * Internal: closes the manager of {@code owner}, waiting up to 30 s for its writes. The core calls it as the owner
     * starts shutting down, right after its regions, while the owner's storage is still open.
     */
    public static void closeOwnedBy(ECPluginData owner) {
        GroupManager manager = MANAGERS.get(owner);
        if (manager == null) {
            return;
        }
        try {
            manager.close().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException slow) {
            owner.getLog().warning("Groups of {} were not all written within {} s of its shutdown; what was not written is lost.",
                    nameOf(owner), CLOSE_TIMEOUT_SECONDS);
        } catch (ExecutionException failed) {
            owner.getLog().warning("Groups of {} could not all be written on shutdown: {}", nameOf(owner), failed.getCause().toString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal: the safety net for an owner whose storage already closed - dissolves its TRANSIENT groups, forgets the rest
     * and writes nothing; pending changes are dropped and logged.
     */
    public static void discardOwnedBy(ECPluginData owner) {
        GroupManager manager = MANAGERS.get(owner);
        if (manager == null) {
            return;
        }
        manager.dissolveTransient();
        synchronized (MANAGERS) {
            manager.closed = true;
            MANAGERS.remove(owner, manager);
        }
        manager.failPendingLoads();
        if (!manager.dirty.isEmpty() || !manager.removed.isEmpty()) {
            ECDebugModule.GROUPS.debug("{} disabled before its groups were written: {} change(s) and {} deletion(s) are dropped.",
                    nameOf(owner), manager.dirty.size(), manager.removed.size());
        }
        manager.dirty.clear();
        manager.removed.clear();
        for (Group group : manager.groups.values()) {
            group.detach();
        }
        manager.groups.clear();
    }

    /** Internal: closes every open manager - the core's own shutdown. */
    public static void closeAll() {
        for (ECPluginData owner : new ArrayList<>(MANAGERS.keySet())) {
            closeOwnedBy(owner);
        }
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    /** A stored deadline (a timed member, an invite) or a sampled rule starts the sweeper. */
    private void wakeIfDue() {
        for (Group group : groups.values()) {
            if (group.getNextDeadline() != null || group.isSampled()) {
                RegionSweeper.wake();
                return;
            }
        }
    }

    private static String nameOf(ECPluginData owner) {
        return owner.getMetaInfo().getName();
    }

    /** A future of the caller's own: completing or cancelling it leaves {@code source} alone. */
    private static CompletableFuture<Void> follow(CompletableFuture<Void> source) {
        CompletableFuture<Void> copy = new CompletableFuture<>();
        source.whenComplete((ignored, error) -> {
            if (error == null) {
                copy.complete(null);
            } else {
                copy.completeExceptionally(error);
            }
        });
        return copy;
    }

    @Override
    public String toString() {
        return "GroupManager(" + nameOf(owner) + ")";
    }

    /** One load, from its first try until it loads or its policy gives up. */
    private static final class LoadRun {
        final Instant startedAt = ECClock.now();
        final RetryPolicy policy;
        final CompletableFuture<Void> firstTry = new CompletableFuture<>();
        final CompletableFuture<Void> done = new CompletableFuture<>();
        volatile int attempts;

        LoadRun(RetryPolicy policy) {
            this.policy = policy;
        }
    }

    private final class ManagedHost implements Group.Host {
        @Override
        public boolean isLive(Group group) {
            return !closed && groups.get(group.getName()) == group;
        }

        @Override
        public Object lock() {
            return lock;
        }

        @Override
        public void changed(Group group, boolean stored, @Nullable Instant deadline) {
            RegionLookup.get().invalidate(group);
            if (stored) {
                markDirty(group);
            }
            if (deadline != null) {
                RegionSweeper.wake();
            }
        }

        @Override
        public void memberChanged(Group group, UUID player) {
            //the group's ACTOR values follow its members: what the player's session holds from them is stale
            RegionLookup.get().invalidate(player);
        }

        @Override
        public void flagChanged(Group group, Flag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue,
                                FlagChangeCause cause) {
            markDirty(group);
            for (GroupMember member : group.getMembers()) {
                RegionLookup.get().invalidate(member.getUuid());
            }
            if (group.getNextDeadline() != null) {
                RegionSweeper.wake();
            }
            if (isLive(group)) {
                ECEventBus.global().postIfListened(GroupFlagChangedEvent.class,
                        () -> new GroupFlagChangedEvent(group, flag, oldValue, newValue, cause));
            }
        }

        @Override
        public @Nullable Group groupOf(UUID player, GroupKind kind) {
            for (Group group : groups.values()) {
                if (group.getKind().equals(kind) && group.isMember(player)) {
                    return group;
                }
            }
            return null;
        }

        @Override
        public void dissolved(Group group, DissolveCause cause) {
            if (!groups.remove(group.getName(), group)) {
                return;
            }
            dirty.remove(group);
            if (group.getPersistence() == Persistence.PERSISTENT && groupStorage != null && !closed) {
                removed.add(group.getName());
                scheduleFlush();
            }
            ECDebugModule.GROUPS.debug("Group {} dissolved ({}).", group.getId(), cause);
        }
    }
}
