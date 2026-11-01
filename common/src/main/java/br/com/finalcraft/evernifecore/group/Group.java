package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
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
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupInviteEvent;
import br.com.finalcraft.evernifecore.group.event.GroupLeaderChangedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberPreJoinEvent;
import br.com.finalcraft.evernifecore.group.event.GroupRoleChangedEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.storage.Persistence;
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
 * {@code max-members}, and post {@link GroupMemberPreJoinEvent} (cancellable) and the after-the-fact events.</p>
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

        /** {@code player} joined, left or lost the membership by time. */
        default void memberChanged(Group group, UUID player) {
        }
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
    // live behaviour: code, never stored
    private volatile @Nullable String scope;
    private volatile @Nullable Edge joinEdge;
    private volatile @Nullable Edge leaveEdge;
    private volatile @Nullable Condition kickWhen;
    private volatile @Nullable FancyText kickReason;
    private volatile QuitPolicy quitPolicy;
    private volatile @Nullable Roster roster;
    /** Players a live roster selected whose join was refused: tried again only once they stopped passing. */
    private final Set<UUID> rosterRefused = ConcurrentHashMap.newKeySet();
    private volatile boolean dissolveWhenEmpty;
    private volatile @Nullable Instant dissolveAt;

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
        this.flags = new FlagStore(this, (flag, oldValue, newValue, cause) -> {
            host.flagChanged(this, flag, oldValue, newValue, cause);
            GroupShareListener.track(this);
            GroupGlow.refresh(this);
        });
        //a region's group stays as the region keeps it; a group of a manager that writes nothing ends with its members' session
        this.quitPolicy = region == null && persistence == Persistence.TRANSIENT ? QuitPolicy.LEAVE : QuitPolicy.KEEP;
        GroupQuitWatch.track(this, quitPolicy == QuitPolicy.LEAVE);
        GroupShareListener.track(this);   //a kind's default may share already
    }

    /** Internal: its manager closed - the group stops taking quits, sharing and glowing, without dissolving. */
    void detach() {
        GroupQuitWatch.track(this, false);
        GroupShareListener.forget(this);
        GroupGlow.forget(this);
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

    /** The free text {@code Groups.ephemeral(...).scope(...)} set ({@code theduel:arena-1/wave-7}); null otherwise. */
    public @Nullable String getScope() {
        return scope;
    }

    void setScope(@Nullable String scope) {
        this.scope = scope;
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
     * roles and join time, and gets the new deadline. False when the group is full ({@code max-members}) or a
     * {@link GroupMemberPreJoinEvent} listener cancelled.
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
        if (fullRefusal() != null) {
            return false;
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
        host.memberChanged(this, uuid);
        GroupGlow.refresh(this);
        post(GroupMemberLeftEvent.class, () -> new GroupMemberLeftEvent(this, uuid, gone, cause));
        if (wasLeader) {
            post(GroupLeaderChangedEvent.class, () -> new GroupLeaderChangedEvent(this, uuid, null));
        }
        if (dissolveWhenEmpty && !dissolved && members.isEmpty()) {
            dissolve(DissolveCause.EMPTY, Collections.singletonList(gone));   //the last members are who emptied it
        }
        return true;
    }

    /**
     * The owner takes {@code uuid} out ({@link LeaveCause#KICKED}) and, when they are online and a reason is given,
     * tells them through the {@link Notifier}. False when not a member.
     */
    public boolean kick(UUID uuid, @Nullable FancyText reason) {
        if (!remove(uuid, LeaveCause.KICKED)) {
            return false;
        }
        FPlayer online = reason == null ? null : online(uuid);
        if (online != null) {
            Notifier.get().notify(online, Notification.of(NotifyKind.INFO).text(reason).build());
        }
        return true;
    }

    // ---- membership: the player's path ----

    /**
     * The player joins: refused when the kind is ONE and they are in another group of it, when the group is full
     * ({@code max-members}), when the join {@link Edge}'s
     * gate denies (its costs are taken only when the join goes through), or when a {@link GroupMemberPreJoinEvent}
     * listener cancelled. Once in, the edge's notices go out. A member already passes.
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
        if (refusal == null) {
            refusal = fullRefusal();
        }
        if (refusal != null) {
            return GateResult.denied(ctx, FancyText.of(refusal));
        }
        Edge edge = joinEdge;
        Gate gate = edge == null ? null : edge.getGate();
        GateResult paid = gate == null ? null : gate.pay(ctx);
        if (paid != null && !paid.isPassed()) {
            return paid;
        }
        FancyText denied = admit(player.getUniqueId(), player, cause, null);
        if (denied != null) {
            refund(gate, ctx);
            return GateResult.denied(ctx, denied);
        }
        tellCrossed(edge, player);
        return paid != null ? paid : GateResult.passed(ctx);
    }

    /**
     * The player leaves ({@link LeaveCause#LEFT}); refused when they are not a member or the leave {@link Edge}'s gate
     * denies. Once out, the edge's notices go out.
     */
    public GateResult leave(FPlayer player) {
        ExecutionContext ctx = contextOf(player);
        if (!isMember(player.getUniqueId())) {
            return notAMember(ctx, player);
        }
        Edge edge = leaveEdge;
        Gate gate = edge == null ? null : edge.getGate();
        GateResult paid = gate == null ? null : gate.pay(ctx);
        if (paid != null && !paid.isPassed()) {
            return paid;
        }
        if (!remove(player.getUniqueId(), LeaveCause.LEFT)) {   //someone else took them out meanwhile
            refund(gate, ctx);
            return notAMember(ctx, player);
        }
        tellCrossed(edge, player);
        return paid != null ? paid : GateResult.passed(ctx);
    }

    private GateResult notAMember(ExecutionContext ctx, FPlayer player) {
        return GateResult.denied(ctx, FancyText.of(player.getName() + " is not a member of the " + kindName() + " '" + getName() + "'."));
    }

    /** Gives back what an edge's gate took for a crossing that did not happen. */
    private static void refund(@Nullable Gate gate, ExecutionContext ctx) {
        if (gate == null) {
            return;
        }
        List<Cost> costs = gate.getCosts();
        for (int i = costs.size() - 1; i >= 0; i--) {
            costs.get(i).refund(ctx);
        }
    }

    /** Sends the edge's notices about {@code player}, who just crossed it. */
    private void tellCrossed(@Nullable Edge edge, FPlayer player) {
        if (edge == null || edge.getNotices().isEmpty()) {
            return;
        }
        ExecutionContext ctx = ExecutionContext.builder(Caller.of(owner, "group/" + getName())).player(player)
                .variable("group", displayName != null ? displayName : getName()).build();
        for (Edge.Notice notice : edge.getNotices()) {
            List<FPlayer> to = new ArrayList<>();
            EdgeAudience audience = notice.getAudience();
            if (audience == EdgeAudience.PLAYER || audience == EdgeAudience.MEMBERS_AND_PLAYER) {
                to.add(player);
            }
            if (audience == EdgeAudience.MEMBERS || audience == EdgeAudience.MEMBERS_AND_PLAYER) {
                for (FPlayer member : getOnlineMembers()) {
                    if (!member.getUniqueId().equals(player.getUniqueId())) {
                        to.add(member);
                    }
                }
            }
            if (audience == EdgeAudience.LEADER) {
                UUID lead = leader;
                FPlayer online = lead == null || lead.equals(player.getUniqueId()) ? null : online(lead);
                if (online != null) {
                    to.add(online);
                }
            }
            if (!to.isEmpty()) {
                Notifier.get().notify(to, Notification.of(edge.getKind()).text(ctx.resolve(notice.getText())).build());
            }
        }
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
            if (refusal == null) {
                refusal = fullRefusal();
            }
            if (refusal != null) {   //a racing join won: into another group of a ONE kind, or the last free place
                return FancyText.of(refusal);
            }
            member = new GroupMember(uuid, ECClock.now(), expiresAt, Collections.<GroupRole>emptySet(), Collections.<String>emptySet());
            members.put(uuid, member);
        }
        host.changed(this, true, expiresAt);
        host.memberChanged(this, uuid);
        GroupGlow.refresh(this);
        post(GroupMemberJoinedEvent.class, () -> new GroupMemberJoinedEvent(this, uuid, player, cause, member));
        return null;
    }

    /** Why nobody else gets in now: {@code max-members} is reached; null while there is room or no cap. */
    private @Nullable String fullRefusal() {
        Integer max = getEffectiveFlag(CoreFlags.MAX_MEMBERS);
        int size = members.size();
        if (max == null || max <= 0 || size < max) {
            return null;
        }
        return "The " + kindName() + " '" + getName() + "' is full (" + size + "/" + max + ", max-members): raise the flag or remove"
                + " a member.";
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

    /** The members online now. */
    public List<FPlayer> getOnlineMembers() {
        List<FPlayer> online = new ArrayList<>();
        for (GroupMember member : getMembers()) {
            FPlayer player = online(member.getUuid());
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /** Whether any member is online now. */
    public boolean hasOnlineMember() {
        for (GroupMember member : getMembers()) {
            if (online(member.getUuid()) != null) {
                return true;
            }
        }
        return false;
    }

    // ---- live behaviour: code, never stored ----

    /** The join edge {@link #join} and {@link #accept} go through; null = straight in. */
    public void setJoinEdge(@Nullable Edge edge) {
        this.joinEdge = edge;
    }

    public @Nullable Edge getJoinEdge() {
        return joinEdge;
    }

    /** The leave edge {@link #leave} goes through; null = straight out. */
    public void setLeaveEdge(@Nullable Edge edge) {
        this.leaveEdge = edge;
    }

    public @Nullable Edge getLeaveEdge() {
        return leaveEdge;
    }

    /**
     * An online member {@code condition} passes for is {@link #kick kicked} with {@code reason}, tested at every sweep
     * (once a second by default). Null removes the rule.
     */
    public void setKickWhen(@Nullable Condition condition, @Nullable FancyText reason) {
        this.kickWhen = condition;
        this.kickReason = reason;
        if (condition != null) {
            host.changed(this, false, ECClock.now());
        }
    }

    public @Nullable Condition getKickWhen() {
        return kickWhen;
    }

    /** What a member's quit does: LEAVE takes them out ({@link LeaveCause#QUIT}), KEEP leaves them offline. */
    public void setQuitPolicy(QuitPolicy policy) {
        this.quitPolicy = Objects.requireNonNull(policy, "policy");
        GroupQuitWatch.track(this, policy == QuitPolicy.LEAVE);
    }

    /** LEAVE by default for a TRANSIENT group of a manager, KEEP for the others. */
    public QuitPolicy getQuitPolicy() {
        return quitPolicy;
    }

    /**
     * Who the group holds on its own: a snapshot adds its players now, a live roster follows its condition from now
     * on (see {@link Roster#live}). Null stops a live roster; the members stay.
     *
     * @throws IllegalStateException when the group is not TRANSIENT - a stored group's members are the owner's to keep
     */
    public void setRoster(@Nullable Roster roster) {
        if (roster != null && persistence != Persistence.TRANSIENT) {
            throw new IllegalStateException("Group " + id + " is " + persistence + ", and a roster belongs to TRANSIENT groups only:"
                    + " build it with Groups.ephemeral(owner, kind), or add the members with add(uuid).");
        }
        requireAlive();
        this.roster = roster;
        rosterRefused.clear();
        if (roster == null) {
            return;
        }
        for (UUID uuid : roster.getMembers()) {
            if (!isMember(uuid) && cardinalityRefusal(uuid, uuid.toString()) == null) {
                admit(uuid, online(uuid), JoinCause.ROSTER, null);
            }
        }
        Condition who = roster.getCondition();
        if (who != null && !roster.isLive()) {
            for (FPlayer player : EverNifeCore.getPlatform().getOnlinePlayers()) {
                if (!isMember(player.getUniqueId()) && cardinalityRefusal(player.getUniqueId(), player.getName()) == null
                        && Conditions.test(who, contextOf(player)).isPassed()) {
                    admit(player.getUniqueId(), player, JoinCause.ROSTER, null);
                }
            }
        }
        if (roster.isLive()) {
            sample();
            host.changed(this, false, ECClock.now());
        }
    }

    public @Nullable Roster getRoster() {
        return roster;
    }

    /** Sets how the group ends on its own; see {@link Dissolution}. */
    void dissolveWhen(Collection<Dissolution> rules) {
        Instant at = null;
        boolean whenEmpty = false;
        for (Dissolution rule : rules) {
            whenEmpty |= rule.isWhenEmpty();
            at = earliest(at, rule.deadlineFor(createdAt));
        }
        this.dissolveWhenEmpty = whenEmpty;
        this.dissolveAt = at;
        if (at != null) {
            host.changed(this, false, at);
        }
    }

    /** Internal: whether the sweeper tests this group at every pass (a live roster or a kick rule). */
    public boolean isSampled() {
        Roster current = roster;
        return !dissolved && (kickWhen != null || current != null && current.isLive());
    }

    /**
     * Internal: one pass of the live roster and the kick rule over the online players - what the sweeper runs. A
     * condition that throws is the owner's bug and propagates.
     */
    public void sample() {
        if (dissolved) {
            return;
        }
        Roster current = roster;
        if (current != null && current.isLive() && current.getCondition() != null) {
            for (FPlayer player : EverNifeCore.getPlatform().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (Conditions.test(current.getCondition(), contextOf(player)).isPassed()) {
                    if (!isMember(uuid) && !rosterRefused.contains(uuid) && !join(player, JoinCause.ROSTER).isPassed()) {
                        rosterRefused.add(uuid);
                    }
                } else {
                    rosterRefused.remove(uuid);
                    if (isMember(uuid)) {
                        remove(uuid, LeaveCause.ROSTER);
                    }
                }
            }
        }
        Condition kick = kickWhen;
        if (kick != null) {
            for (GroupMember member : getMembers()) {
                FPlayer player = online(member.getUuid());
                if (player != null && Conditions.test(kick, contextOf(player)).isPassed()) {
                    kick(member.getUuid(), kickReason);
                }
            }
        }
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

    /**
     * The group's own live value, else its kind's default ({@link GroupKind#getFlagDefaults()}) as a permanent value set
     * when the group was created; null for neither. What the flag resolution reads, so a kind's default speaks there too.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable FlagValue<T> getFlagValue(Flag<T> flag) {
        FlagValue<T> own = flags.getValue(flag);
        if (own != null) {
            return own;
        }
        T fallback = (T) kind.getFlagDefaults().get(flag.getKey());
        return fallback == null ? null : FlagValue.permanent(fallback).withSince(createdAt);
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

    /** The group's own live value of {@code flag}, else its kind's default ({@link GroupKind#getFlagDefaults()}); null for neither. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getEffectiveFlag(Flag<T> flag) {
        T own = getFlag(flag);
        return own != null ? own : (T) kind.getFlagDefaults().get(flag.getKey());
    }

    // ---- dissolution ----

    /**
     * Ends the group: every member leaves with {@link LeaveCause#DISSOLVED}, the invites go, {@link GroupDissolvedEvent}
     * carries the last members, and its host forgets it. A second call does nothing.
     */
    public void dissolve(DissolveCause cause) {
        dissolve(cause, null);
    }

    private void dissolve(DissolveCause cause, @Nullable List<GroupMember> lastOverride) {
        Objects.requireNonNull(cause, "cause");
        List<GroupMember> last;
        synchronized (host.lock()) {
            if (dissolved) {
                return;
            }
            last = new ArrayList<>(getMembers());
            dissolved = true;
        }
        GroupQuitWatch.track(this, false);
        GroupShareListener.track(this);
        GroupGlow.refresh(this);
        roster = null;
        kickWhen = null;
        boolean live = host.isLive(this);
        for (GroupMember member : last) {
            members.remove(member.getUuid());
            host.memberChanged(this, member.getUuid());
            if (live) {
                ECEventBus.global().postIfListened(GroupMemberLeftEvent.class,
                        () -> new GroupMemberLeftEvent(this, member.getUuid(), member, LeaveCause.DISSOLVED));
            }
        }
        members.clear();
        invites.clear();
        leader = null;
        if (live) {
            List<GroupMember> lastMembers = Collections.unmodifiableList(lastOverride != null ? lastOverride : last);
            ECEventBus.global().postIfListened(GroupDissolvedEvent.class, () -> new GroupDissolvedEvent(this, cause, lastMembers));
        }
        host.dissolved(this, cause);
    }

    // ---- what the sweeper and the storage use ----

    /** Internal: the earliest deadline among members, invites and timed flag values, expired ones included; null when none. */
    public @Nullable Instant getNextDeadline() {
        Instant next = earliest(flags.getNextDeadline(), dissolveAt);
        for (GroupMember member : members.values()) {
            next = earliest(next, member.getExpiresAt());
        }
        for (Invite invite : invites.values()) {
            next = earliest(next, invite.getExpiresAt());
        }
        return next;
    }

    /**
     * Internal: drops what is due at {@code now} - the group itself when a {@link Dissolution} time came
     * ({@link DissolveCause#EXPIRED}), else members ({@link LeaveCause#EXPIRED}), invites
     * ({@link GroupInviteEvent.Change#EXPIRED}) and timed flag values.
     */
    public void expire(Instant now) {
        Instant end = dissolveAt;
        if (end != null && !now.isBefore(end)) {
            dissolve(DissolveCause.EXPIRED);
            return;
        }
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

    private static @Nullable FPlayer online(UUID uuid) {
        FPlayer player = EverNifeCore.getPlatform().getPlayer(uuid);
        return player != null && player.isOnline() ? player : null;
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
