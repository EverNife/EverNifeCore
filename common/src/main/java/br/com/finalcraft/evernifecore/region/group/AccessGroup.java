package br.com.finalcraft.evernifecore.region.group;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.RegionTime;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagStore;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * A named set of players inside one region, with flag values of its own that override the region's for them. A
 * player belongs by any of three means at once: as an explicit member (UUID, optionally until a deadline), by the
 * owner's predicate (never persisted - set again on every enable) or by a permission. No roles, no inheritance
 * between groups.
 *
 * <p>Equality is identity on purpose: sessions cache membership by group object.</p>
 */
public final class AccessGroup {

    /** Every mutation of a group lands here, after the fact. */
    @FunctionalInterface
    public interface ChangeHook {
        void onGroupChanged(AccessGroup group, GroupChange change, @Nullable UUID player);
    }

    private final Region region;
    private final String name;
    private final ChangeHook hook;
    private final FlagStore flags;
    private final ConcurrentHashMap<UUID, Membership> members = new ConcurrentHashMap<>();
    private volatile int priority;
    private volatile @Nullable Predicate<FPlayer> predicate;
    private volatile @Nullable String permission;
    private volatile MembershipCheck check = MembershipCheck.exact();

    /** Internal: groups are made by {@link Region#getGroup(String)}. */
    public AccessGroup(Region region, String name, ChangeHook hook) {
        this.region = Objects.requireNonNull(region, "region");
        this.name = Objects.requireNonNull(name, "name");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.flags = new FlagStore(this::onFlagChanged);
    }

    public String getName() {
        return name;
    }

    public Region getRegion() {
        return region;
    }

    /** Default 0; among the groups of one player that define a flag, the highest priority wins. */
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        hook.onGroupChanged(this, GroupChange.PRIORITY_SET, null);
    }

    // ---- flags ----

    /** A read-only snapshot of the group's live values. */
    public Map<FlagKey, FlagValue<?>> getFlags() {
        return flags.getFlags();
    }

    /** The group's own live value, member or not; null when unset or expired. */
    public <T> @Nullable T getFlag(RegionFlag<T> flag) {
        FlagValue<T> value = flags.getValue(flag);
        return value == null ? null : value.getValue();
    }

    public <T> void setFlag(RegionFlag<T> flag, T value) {
        flags.set(flag, value);
    }

    public <T> void setFlag(RegionFlag<T> flag, T value, Duration ttl) {
        flags.set(flag, value, ttl);
    }

    public boolean removeFlag(RegionFlag<?> flag) {
        return flags.remove(flag);
    }

    /** Internal: the store behind the flag methods, shared with the flag registry. */
    public FlagStore getFlagStore() {
        return flags;
    }

    private void onFlagChanged(RegionFlag<?> flag, @Nullable FlagValue<?> oldValue, @Nullable FlagValue<?> newValue, FlagChangeCause cause) {
        hook.onGroupChanged(this, cause == FlagChangeCause.REMOVED ? GroupChange.FLAG_REMOVED : GroupChange.FLAG_SET, null);
    }

    // ---- explicit members ----

    public Membership addMember(UUID uuid) {
        return addMember(uuid, null);
    }

    /** @param ttl how long the membership lasts; null for good */
    public Membership addMember(UUID uuid, @Nullable Duration ttl) {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("A membership of group " + name + " needs a positive duration, not " + ttl
                    + ": use addMember(uuid) for a permanent one.");
        }
        Instant now = RegionTime.now();
        Membership membership = new Membership(Objects.requireNonNull(uuid, "uuid"), now, ttl == null ? null : now.plus(ttl));
        members.put(uuid, membership);
        hook.onGroupChanged(this, GroupChange.MEMBER_ADDED, uuid);
        return membership;
    }

    /** False when {@code uuid} was not a member. */
    public boolean removeMember(UUID uuid) {
        if (members.remove(uuid) == null) {
            return false;
        }
        hook.onGroupChanged(this, GroupChange.MEMBER_REMOVED, uuid);
        return true;
    }

    /** The explicit members whose membership has not expired. Read-only. */
    public Collection<Membership> getMembers() {
        List<Membership> live = new ArrayList<>();
        for (Membership membership : members.values()) {
            if (!membership.isExpired()) {
                live.add(membership);
            }
        }
        return Collections.unmodifiableList(live);
    }

    /** Null when {@code uuid} is not a member or the membership expired. */
    public @Nullable Membership getMembership(UUID uuid) {
        Membership membership = members.get(uuid);
        return membership == null || membership.isExpired() ? null : membership;
    }

    // ---- dynamic membership ----

    /** Never persisted: the owner sets it again on every enable. Null removes it. */
    public void setPredicate(@Nullable Predicate<FPlayer> predicate) {
        this.predicate = predicate;
        hook.onGroupChanged(this, GroupChange.PREDICATE_SET, null);
    }

    public @Nullable Predicate<FPlayer> getPredicate() {
        return predicate;
    }

    /** Players holding this permission belong. Null removes it. */
    public void setPermission(@Nullable String permission) {
        this.permission = permission;
        hook.onGroupChanged(this, GroupChange.PERMISSION_SET, null);
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
        hook.onGroupChanged(this, GroupChange.CHECK_SET, null);
    }

    // ---- queries ----

    /**
     * Whether {@code player} belongs: a live explicit membership, or the predicate or permission - asked again or
     * taken from the player's session as {@link #getMembershipCheck()} says. A predicate that throws is the owner's
     * bug and propagates.
     */
    public boolean contains(FPlayer player) {
        Membership membership = members.get(player.getUniqueId());
        if (membership != null && !membership.isExpired()) {
            return true;
        }
        if (predicate == null && permission == null) {
            return false;
        }
        MembershipCheck current = check;
        if (current.getKind() == MembershipCheck.Kind.EXACT) {
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

    /** The group's value of {@code flag} for {@code player}: null when the player does not belong or the group does not define it. */
    public <T> @Nullable T resolve(RegionFlag<T> flag, FPlayer player) {
        T value = getFlag(flag);
        return value != null && contains(player) ? value : null;
    }

    @Override
    public String toString() {
        return region.getId() + "/" + name;
    }
}
