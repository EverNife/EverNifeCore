package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.flag.FlagHolder;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagStore;
import br.com.finalcraft.evernifecore.flag.FlagValue;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupInviteEvent;
import br.com.finalcraft.evernifecore.group.event.GroupLeaderChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberPreJoinEvent;
import br.com.finalcraft.evernifecore.group.event.GroupRoleChangedEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.time.ECClock;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One group of players: explicit members with roles and an optional deadline, an optional leader, invites, and flag
 * values of its own. A group of a {@link GroupManager} carries {@link FlagScope#ACTOR} values that follow its members;
 * the group local to a region ({@link GroupKind#REGION_LOCAL}, made by {@link Region#getGroup(String)}) speaks about
 * that place, for its members, as a {@link FlagScope#PLACE} holder.
 *
 * <p>Two ways in and out: {@link #add}/{@link #remove} are the owner's direct calls, {@link #join}/{@link #leave} the
 * player's path, which answers a {@link GateResult} that explains a refusal. Both check the kind's cardinality and
 * post {@link GroupMemberPreJoinEvent} (cancellable) and the after-the-fact events.</p>
 *
 * <p>Besides its explicit members, a player belongs ({@link #contains}) by the owner's predicate (never stored) or by a
 * permission; that virtual membership has no {@link GroupMember} and no event.</p>
 *
 * <p><b>Threads:</b> any. Membership writes of one manager (or one region) are serialized by its lock, reads take no
 * lock, and every event goes out on the thread of whoever made the change. Equality is identity on purpose: sessions
 * cache membership by group object.</p>
 */
public final class Group implements FlagHolder {

    /** Internal: who keeps a group - the region of a region-local group, or the manager of the others. */
    public interface Host {
        /** Whether changes are posted and written: false while a load builds the group, and once it is gone. */
        boolean isLive(Group group);

        /** The lock membership writes of every group of this host hold. */
        Object lock();

        /**
         * Something of the group changed, after the fact.
         *
         * @param stored   whether the change is part of what storage keeps (the predicate is not)
         * @param deadline a deadline the change brought (a timed member, an invite), for the sweeper; null for none
         */
        void changed(Group group, boolean stored, @Nullable Instant deadline);

        void flagChanged(Group group, Flag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue, FlagChangeCause cause);

        /** The other group of {@code kind} that {@code player} is a member of, for a ONE kind; null when none. */
        @Nullable Group groupOf(UUID player, GroupKind kind);

        /** The group was dissolved: forget it. */
        void dissolved(Group group, DissolveCause cause);
    }

    private final GroupId id;
    private final GroupKind kind;
    private final ECPluginData owner;
    private final Persistence persistence;
    private final @Nullable Region region;
    private final Instant createdAt;
    private final Host host;
    private final FlagStore flags;
    private final ConcurrentHashMap<UUID, GroupMember> members = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Invite> invites = new ConcurrentHashMap<>();
    private volatile int priority;
    private volatile @Nullable UUID leader;
    private volatile @Nullable String displayName;
    private volatile @Nullable JsonNode metadata;
    private volatile @Nullable Predicate<FPlayer> predicate;
    private volatile @Nullable String permission;
    private volatile MembershipCheck check = MembershipCheck.exact();
    private volatile boolean dissolved;

    /** Internal: groups are made by {@link GroupManager#create} and {@link Region#getGroup(String)}. */
    public Group(GroupId id, GroupKind kind, ECPluginData owner, Persistence persistence, @Nullable Region region, Instant createdAt,
                 Host host) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.region = region;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.host = Objects.requireNonNull(host, "host");
        this.flags = new FlagStore(this, (flag, oldValue, newValue, cause) -> host.flagChanged(this, flag, oldValue, newValue, cause));
    }

    public GroupId getId() {
        return id;
    }

    /** The group's own name: the part of the id after the owner (after the region for a region-local group). */
    public String getName() {
        return id.getName();
    }

    public GroupKind getKind() {
        return kind;
    }

    /** The plugin that owns the group; for a region-local group, the region's owner. */
    public ECPluginData getOwner() {
        return owner;
    }

    /** TRANSIENT never reaches the storage. A region-local group shares its region's. */
    public Persistence getPersistence() {
        return persistence;
    }

    /** The region that owns a region-local group; null for the groups of a manager. */
    public @Nullable Region getRegion() {
        return region;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDissolved() {
        return dissolved;
    }

    public @Nullable String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
        host.changed(this, true, null);
    }

    public @Nullable JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(@Nullable JsonNode metadata) {
        this.metadata = metadata;
        host.changed(this, true, null);
    }

    // ---- leader ----

    public @Nullable UUID getLeader() {
        return leader;
    }

    /**
     * Hands the lead to {@code uuid}, or leaves the group without one. Posts {@link GroupLeaderChangedEvent} when it changed.
     *
     * @throws IllegalArgumentException when {@code uuid} is not a member
     */
    public void setLeader(@Nullable UUID uuid) {
        UUID old;
        synchronized (host.lock()) {
            if (uuid != null && getMember(uuid) == null) {
                throw new IllegalArgumentException(uuid + " is not a member of group " + id + ", so it cannot lead it: add(uuid) it first,"
                        + " or setLeader(null) to leave the group without a leader.");
            }
            old = leader;
            leader = uuid;
        }
        if (!Objects.equals(old, uuid)) {
            host.changed(this, true, null);
            post(GroupLeaderChangedEvent.class, () -> new GroupLeaderChangedEvent(this, old, uuid));
        }
    }

    // ---- membership: the owner's direct calls ----

    /**
     * Adds a permanent member - see {@link #add(UUID, Duration)}.
     *
     * @throws IllegalStateException when the kind is ONE and the player is in another group of it; the message says how out
     */
    public boolean add(UUID uuid) {
        return add(uuid, null);
    }

    /**
     * Adds {@code uuid} as a member until now + {@code ttl} (null = for good). A player already a member keeps their
     * roles and join time, and gets the new deadline. False when a {@link GroupMemberPreJoinEvent} listener cancelled.
     *
     * @throws IllegalArgumentException when {@code ttl} is not positive
     * @throws IllegalStateException    when the kind is ONE and the player is in another group of it; the message says how out
     */
    public boolean add(UUID uuid, @Nullable Duration ttl) {
        Objects.requireNonNull(uuid, "uuid");
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("A membership of group " + id + " needs a positive duration, not " + ttl
                    + ": use add(uuid) for a permanent one.");
        }
        requireAlive();
        Instant expiresAt = ttl == null ? null : ECClock.now().plus(ttl);
        GroupMember current = getMember(uuid);
        if (current != null) {
            members.put(uuid, current.withExpiresAt(expiresAt));
            host.changed(this, true, expiresAt);
            return true;
        }
        String refusal = cardinalityRefusal(uuid, uuid.toString());
        if (refusal != null) {
            throw new IllegalStateException(refusal);
        }
        return admit(uuid, null, JoinCause.ADDED, expiresAt) == null;
    }

    /**
     * Takes {@code uuid} out of the group with {@code cause}, posting {@link GroupMemberLeftEvent}; a leader that leaves
     * leaves the group without one ({@link GroupLeaderChangedEvent} to null). False when it was not a member.
     */
    public boolean remove(UUID uuid, LeaveCause cause) {
        Objects.requireNonNull(cause, "cause");
        GroupMember gone;
        boolean wasLeader;
        synchronized (host.lock()) {
            gone = members.remove(uuid);
            wasLeader = gone != null && uuid.equals(leader);
            if (wasLeader) {
                leader = null;
            }
        }
        if (gone == null) {
            return false;
        }
        host.changed(this, true, null);
        post(GroupMemberLeftEvent.class, () -> new GroupMemberLeftEvent(this, uuid, gone, cause));
        if (wasLeader) {
            post(GroupLeaderChangedEvent.class, () -> new GroupLeaderChangedEvent(this, uuid, null));
        }
        return true;
    }

    // ---- membership: the player's path ----

    /**
     * The player joins: refused when the kind is ONE and they are in another group of it, or when a
     * {@link GroupMemberPreJoinEvent} listener cancelled. A member already passes.
     */
    public GateResult join(FPlayer player) {
        return join(player, JoinCause.JOINED);
    }

    private GateResult join(FPlayer player, JoinCause cause) {
        ExecutionContext ctx = contextOf(player);
        if (dissolved) {
            return GateResult.denied(ctx, FancyText.of("The " + kindName() + " '" + getName() + "' no longer exists."));
        }
        if (isMember(player.getUniqueId())) {
            return GateResult.passed(ctx);
        }
        String refusal = cardinalityRefusal(player.getUniqueId(), player.getName());
        if (refusal != null) {
            return GateResult.denied(ctx, FancyText.of(refusal));
        }
        FancyText denied = admit(player.getUniqueId(), player, cause, null);
        return denied == null ? GateResult.passed(ctx) : GateResult.denied(ctx, denied);
    }

    /** The player leaves ({@link LeaveCause#LEFT}); refused when they are not a member. */
    public GateResult leave(FPlayer player) {
        ExecutionContext ctx = contextOf(player);
        if (!remove(player.getUniqueId(), LeaveCause.LEFT)) {
            return GateResult.denied(ctx, FancyText.of(player.getName() + " is not a member of the " + kindName() + " '" + getName() + "'."));
        }
        return GateResult.passed(ctx);
    }

    /** Posts the pre event and, unless cancelled, makes the member; null on success, else why it was refused. */
    private @Nullable FancyText admit(UUID uuid, @Nullable FPlayer player, JoinCause cause, @Nullable Instant expiresAt) {
        if (host.isLive(this)) {
            GroupMemberPreJoinEvent pre = ECEventBus.global().postIfListened(GroupMemberPreJoinEvent.class,
                    () -> new GroupMemberPreJoinEvent(this, uuid, player, cause));
            if (pre != null && pre.isCancelled()) {
                return pre.getDenyReason() != null ? pre.getDenyReason()
                        : FancyText.of("Joining the " + kindName() + " '" + getName() + "' was refused.");
            }
        }
        GroupMember member;
        synchronized (host.lock()) {
            String refusal = cardinalityRefusal(uuid, player == null ? uuid.toString() : player.getName());
            if (refusal != null) {   //a racing join into another group of a ONE kind won
                return FancyText.of(refusal);
            }
            member = new GroupMember(uuid, ECClock.now(), expiresAt, Collections.<GroupRole>emptySet(), Collections.<String>emptySet());
            members.put(uuid, member);
        }
        host.changed(this, true, expiresAt);
        post(GroupMemberJoinedEvent.class, () -> new GroupMemberJoinedEvent(this, uuid, player, cause, member));
        return null;
    }

    private @Nullable String cardinalityRefusal(UUID uuid, String playerName) {
        if (kind.getCardinality() != GroupKind.Cardinality.ONE) {
            return null;
        }
        Group other = host.groupOf(uuid, kind);
        if (other == null || other == this) {
            return null;
        }
        return playerName + " is already in the " + kindName() + " '" + other.getName() + "' (" + kind.getKey()
                + " allows one group per player): leave it first, or make the kind MANY.";
    }

    /** The live, unexpired members. Read-only snapshot. */
    public Collection<GroupMember> getMembers() {
        List<GroupMember> live = new ArrayList<>();
        for (GroupMember member : members.values()) {
            if (!member.isExpired()) {
                live.add(member);
            }
        }
        return Collections.unmodifiableList(live);
    }

    /** Null when {@code uuid} is not a member or the membership expired. */
    public @Nullable GroupMember getMember(UUID uuid) {
        GroupMember member = members.get(uuid);
        return member == null || member.isExpired() ? null : member;
    }

    /** An explicit, unexpired member - the predicate and permission do not count; see {@link #contains}. */
    public boolean isMember(UUID uuid) {
        return getMember(uuid) != null;
    }

    public int size() {
        return getMembers().size();
    }

    // ---- roles ----

    /**
     * Replaces the roles of a member; posts {@link GroupRoleChangedEvent} when they changed. False when not a member. A
     * region-local group has no ladder: any role is a free tag there.
     *
     * @throws IllegalArgumentException naming the kind's ladder when a role is not on it
     */
    public boolean setRoles(UUID uuid, Set<GroupRole> roles) {
        for (GroupRole role : roles) {
            if (region == null && !kind.getRoles().contains(role)) {
                throw new IllegalArgumentException("Role " + role + " is not on the ladder of group kind " + kind.getKey() + ", which is "
                        + kind.getRoles() + ": use one of those, or register the kind with the role.");
            }
        }
        GroupMember old;
        GroupMember updated;
        synchronized (host.lock()) {
            old = getMember(uuid);
            if (old == null) {
                return false;
            }
            updated = old.withRoles(new LinkedHashSet<>(roles));
            if (old.getRoles().equals(updated.getRoles())) {
                return true;
            }
            members.put(uuid, updated);
        }
        host.changed(this, true, null);
        post(GroupRoleChangedEvent.class, () -> new GroupRoleChangedEvent(this, uuid, old.getRoles(), updated.getRoles()));
        return true;
    }

    /** @see #setRoles */
    public boolean addRole(UUID uuid, GroupRole role) {
        GroupMember member = getMember(uuid);
        if (member == null) {
            return false;
        }
        Set<GroupRole> roles = new LinkedHashSet<>(member.getRoles());
        roles.add(role);
        return setRoles(uuid, roles);
    }

    /** @see #setRoles */
    public boolean removeRole(UUID uuid, GroupRole role) {
        GroupMember member = getMember(uuid);
        if (member == null) {
            return false;
        }
        Set<GroupRole> roles = new LinkedHashSet<>(member.getRoles());
        roles.remove(role);
        return setRoles(uuid, roles);
    }

    // ---- invites ----

    /**
     * Invites {@code uuid} until now + {@code ttl}; a pending invite of the player is replaced. Posts
     * {@link GroupInviteEvent} SENT.
     *
     * @throws IllegalArgumentException when {@code ttl} is not positive
     */
    public Invite invite(UUID uuid, Duration ttl, @Nullable UUID invitedBy) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("An invite into group " + id + " needs a positive duration, not " + ttl
                    + ": an invite always expires, e.g. Duration.ofMinutes(5).");
        }
        requireAlive();
        Instant now = ECClock.now();
        Invite invite = new Invite(id, Objects.requireNonNull(uuid, "uuid"), invitedBy, now, now.plus(ttl));
        invites.put(uuid, invite);
        host.changed(this, true, invite.getExpiresAt());
        post(GroupInviteEvent.class, () -> new GroupInviteEvent(this, invite, GroupInviteEvent.Change.SENT));
        return invite;
    }

    /** Takes back the pending invite of {@code uuid}; false when there was none. */
    public boolean revokeInvite(UUID uuid) {
        Invite gone = invites.remove(uuid);
        if (gone == null) {
            return false;
        }
        host.changed(this, true, null);
        post(GroupInviteEvent.class, () -> new GroupInviteEvent(this, gone, GroupInviteEvent.Change.REVOKED));
        return true;
    }

    /** The pending, unexpired invites. Read-only snapshot. */
    public Collection<Invite> getInvites() {
        List<Invite> live = new ArrayList<>();
        for (Invite invite : invites.values()) {
            if (!invite.isExpired()) {
                live.add(invite);
            }
        }
        return Collections.unmodifiableList(live);
    }

    /** Null when {@code uuid} has no unexpired invite. */
    public @Nullable Invite getInvite(UUID uuid) {
        Invite invite = invites.get(uuid);
        return invite == null || invite.isExpired() ? null : invite;
    }

    /** Consumes the player's invite ({@link GroupInviteEvent} ACCEPTED), then joins as {@link #join} does. */
    public GateResult accept(FPlayer player) {
        Invite invite = getInvite(player.getUniqueId());
        if (invite == null || !invites.remove(player.getUniqueId(), invite)) {
            return GateResult.denied(contextOf(player), FancyText.of(player.getName() + " has no invite into the " + kindName() + " '"
                    + getName() + "', or it expired: ask for a new one."));
        }
        host.changed(this, true, null);
        post(GroupInviteEvent.class, () -> new GroupInviteEvent(this, invite, GroupInviteEvent.Change.ACCEPTED));
        return join(player, JoinCause.INVITED);
    }

    // ---- virtual membership ----

    /** Never stored: the owner sets it again on every enable. Null removes it. */
    public void setPredicate(@Nullable Predicate<FPlayer> predicate) {
        this.predicate = predicate;
        host.changed(this, false, null);
    }

    public @Nullable Predicate<FPlayer> getPredicate() {
        return predicate;
    }

    /** Players holding this permission belong. Null removes it. */
    public void setPermission(@Nullable String permission) {
        this.permission = permission;
        host.changed(this, true, null);
    }

    public @Nullable String getPermission() {
        return permission;
    }

    public MembershipCheck getMembershipCheck() {
        return check;
    }

    /** How long the predicate and permission answer is kept per player; {@link MembershipCheck#exact()} by default. */
    public void setMembershipCheck(MembershipCheck check) {
        this.check = Objects.requireNonNull(check, "check");
        host.changed(this, true, null);
    }

    /**
     * Whether {@code player} belongs: an explicit member, or the predicate or permission - asked again or taken from the
     * player's session as {@link #getMembershipCheck()} says. A predicate that throws is the owner's bug and propagates.
     */
    public boolean contains(FPlayer player) {
        if (isMember(player.getUniqueId())) {
            return true;
        }
        if (predicate == null && permission == null) {
            return false;
        }
        MembershipCheck current = check;
        if (current.getType() == MembershipCheck.Type.EXACT) {
            return containsNow(player);
        }
        return RegionLookup.get().getSession(player).membership(this, current, () -> containsNow(player));
    }

    private boolean containsNow(FPlayer player) {
        Predicate<FPlayer> currentPredicate = predicate;
        String currentPermission = permission;
        return (currentPredicate != null && currentPredicate.test(player))
                || (currentPermission != null && player.hasPermission(currentPermission));
    }

    // ---- flags ----

    /** Default 0; among the groups that hold a player and define a flag, the highest priority comes first. */
    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        host.changed(this, true, null);
    }

    /** The id: {@code owner:name}, or {@code owner:region/group}. */
    @Override
    public String getHolderId() {
        return id.toString();
    }

    /** PLACE for a region-local group (it speaks about its region), ACTOR for the others (their values follow the members). */
    @Override
    public FlagScope getHolderScope() {
        return region != null ? FlagScope.PLACE : FlagScope.ACTOR;
    }

    /** A read-only snapshot of the group's live values. */
    @Override
    public Map<FlagKey, FlagValue<?>> getFlags() {
        return flags.getFlags();
    }

    /** The group's own live value, member or not; null when unset or expired. */
    public <T> @Nullable T getFlag(Flag<T> flag) {
        FlagValue<T> value = flags.getValue(flag);
        return value == null ? null : value.getValue();
    }

    @Override
    public <T> @Nullable FlagValue<T> getFlagValue(Flag<T> flag) {
        return flags.getValue(flag);
    }

    /** @throws IllegalArgumentException when the group cannot carry the flag (a PLACE flag on a group that is not region-local) */
    public <T> void setFlag(Flag<T> flag, T value) {
        flags.set(flag, value);
    }

    /** Sets the value with its envelope; see {@link Region#setFlag(Flag, FlagValue)}. */
    public <T> void setFlag(Flag<T> flag, FlagValue<T> value) {
        flags.set(flag, value);
    }

    public <T> void setFlag(Flag<T> flag, T value, Duration ttl) {
        flags.set(flag, value, ttl);
    }

    public boolean removeFlag(Flag<?> flag) {
        return flags.remove(flag);
    }

    /** Internal: the store behind the flag methods, shared with the flag registry. */
    @Override
    public FlagStore getFlagStore() {
        return flags;
    }

    /** The group's value of {@code flag} for {@code player}: null when the player does not belong or the group does not define it. */
    public <T> @Nullable T resolve(Flag<T> flag, FPlayer player) {
        T value = getFlag(flag);
        return value != null && contains(player) ? value : null;
    }

    // ---- dissolution ----

    /**
     * Ends the group: every member leaves with {@link LeaveCause#DISSOLVED}, the invites go, {@link GroupDissolvedEvent}
     * carries the last members, and its host forgets it. A second call does nothing.
     */
    public void dissolve(DissolveCause cause) {
        Objects.requireNonNull(cause, "cause");
        List<GroupMember> last;
        synchronized (host.lock()) {
            if (dissolved) {
                return;
            }
            last = new ArrayList<>(getMembers());
            dissolved = true;
        }
        boolean live = host.isLive(this);
        for (GroupMember member : last) {
            members.remove(member.getUuid());
            if (live) {
                ECEventBus.global().postIfListened(GroupMemberLeftEvent.class,
                        () -> new GroupMemberLeftEvent(this, member.getUuid(), member, LeaveCause.DISSOLVED));
            }
        }
        members.clear();
        invites.clear();
        leader = null;
        if (live) {
            List<GroupMember> lastMembers = Collections.unmodifiableList(last);
            ECEventBus.global().postIfListened(GroupDissolvedEvent.class, () -> new GroupDissolvedEvent(this, cause, lastMembers));
        }
        host.dissolved(this, cause);
    }

    // ---- what the sweeper and the storage use ----

    /** Internal: the earliest deadline among members, invites and timed flag values, expired ones included; null when none. */
    public @Nullable Instant getNextDeadline() {
        Instant next = flags.getNextDeadline();
        for (GroupMember member : members.values()) {
            next = earliest(next, member.getExpiresAt());
        }
        for (Invite invite : invites.values()) {
            next = earliest(next, invite.getExpiresAt());
        }
        return next;
    }

    /**
     * Internal: drops what is due at {@code now} - members ({@link LeaveCause#EXPIRED}), invites
     * ({@link GroupInviteEvent.Change#EXPIRED}) and timed flag values.
     */
    public void expire(Instant now) {
        flags.expire(now);
        for (GroupMember member : members.values()) {
            Instant deadline = member.getExpiresAt();
            if (deadline != null && !now.isBefore(deadline) && members.containsKey(member.getUuid())) {
                remove(member.getUuid(), LeaveCause.EXPIRED);
            }
        }
        for (Invite invite : invites.values()) {
            if (!now.isBefore(invite.getExpiresAt()) && invites.remove(invite.getPlayer(), invite)) {
                host.changed(this, true, null);
                post(GroupInviteEvent.class, () -> new GroupInviteEvent(this, invite, GroupInviteEvent.Change.EXPIRED));
            }
        }
    }

    /** Internal: every member as stored, expired ones included. */
    public Collection<GroupMember> getStoredMembers() {
        return Collections.unmodifiableCollection(new ArrayList<>(members.values()));
    }

    /** Internal: every invite as stored, expired ones included. */
    public Collection<Invite> getStoredInvites() {
        return Collections.unmodifiableCollection(new ArrayList<>(invites.values()));
    }

    /**
     * Internal: puts back a member read from storage, as stored; a role name the kind does not list stays raw. Loading is
     * not a change: nothing hears it.
     */
    public void restoreMember(UUID uuid, Instant joinedAt, @Nullable Instant expiresAt, Collection<String> roleNames) {
        Set<GroupRole> roles = new LinkedHashSet<>();
        Set<String> unknown = new HashSet<>();
        for (String roleName : roleNames) {
            GroupRole role = region != null ? GroupRole.of(roleName, 0) : kind.getRole(roleName);
            if (role != null) {
                roles.add(role);
            } else {
                unknown.add(roleName);
            }
        }
        members.put(uuid, new GroupMember(uuid, joinedAt, expiresAt, roles, unknown));
    }

    /** Internal: puts back an invite read from storage. */
    public void restoreInvite(UUID uuid, Instant invitedAt, Instant expiresAt, @Nullable UUID invitedBy) {
        invites.put(uuid, new Invite(id, uuid, invitedBy, invitedAt, expiresAt));
    }

    /** Internal: puts back the stored leader without an event. */
    public void restoreLeader(@Nullable UUID uuid) {
        this.leader = uuid;
    }

    /** Internal: puts back the stored fields without an event or a write. */
    public void restore(int priority, @Nullable String permission, MembershipCheck check, @Nullable String displayName,
                        @Nullable JsonNode metadata) {
        this.priority = priority;
        this.permission = permission;
        this.check = Objects.requireNonNull(check, "check");
        this.displayName = displayName;
        this.metadata = metadata;
    }

    // ---- helpers ----

    private void requireAlive() {
        if (dissolved) {
            throw new IllegalStateException("Group " + id + " was dissolved: it takes no member nor invite any more - create a new one.");
        }
    }

    private <T extends IECEvent> void post(Class<T> type, Supplier<? extends T> event) {
        if (host.isLive(this)) {
            ECEventBus.global().postIfListened(type, event);
        }
    }

    private ExecutionContext contextOf(FPlayer player) {
        return ExecutionContext.builder(Caller.of(owner, "group/" + getName())).player(player).build();
    }

    /** The kind's name for a message: "party" for {@code finalparty:party}, "group" for a region-local group. */
    private String kindName() {
        return region != null ? "group" : kind.getKey().getName();
    }

    private static @Nullable Instant earliest(@Nullable Instant a, @Nullable Instant b) {
        return a == null ? b : b == null || a.isBefore(b) ? a : b;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
