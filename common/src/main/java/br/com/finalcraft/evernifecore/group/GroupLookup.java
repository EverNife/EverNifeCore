package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.ActorHolders;
import br.com.finalcraft.evernifecore.flag.FlagHolder;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The one place to ask about groups, whoever keeps them: every open {@link GroupManager} (always) plus the
 * {@link GroupProvider}s plugins register. It also feeds the ACTOR layer of the flag resolution: the groups that hold a
 * player reach {@link ActorHolders} through {@link #attachToResolver}, which the core does on its enable.
 *
 * <p><b>Threads:</b> any; it takes no lock and does no I/O.</p>
 */
public final class GroupLookup {

    private static final GroupLookup INSTANCE = new GroupLookup();

    private static final class Entry {
        final ECPluginData owner;
        final GroupProvider provider;
        private final AtomicBoolean warned = new AtomicBoolean();

        Entry(ECPluginData owner, GroupProvider provider) {
            this.owner = owner;
            this.provider = provider;
        }

        /** {@code question} asked of this provider, or {@code silence} when it throws - warned once per registration. */
        <T> T ask(Function<GroupProvider, T> question, T silence) {
            try {
                return question.apply(provider);
            } catch (RuntimeException failed) {
                if (warned.compareAndSet(false, true)) {
                    EverNifeCore.getLog().warning("The GroupProvider of " + owner.getMetaInfo().getName() + " ("
                            + provider.getClass().getName() + ") threw " + failed + " - the group lookup goes on without its"
                            + " answers, and further failures of it stay silent until it is registered again. Fix the"
                            + " provider in " + owner.getMetaInfo().getName() + ".");
                }
                return silence;
            }
        }
    }

    private final List<Entry> registered = new CopyOnWriteArrayList<>();
    private volatile @Nullable ECPluginData resolverOwner;

    private GroupLookup() {
    }

    public static GroupLookup get() {
        return INSTANCE;
    }

    /**
     * Adds {@code provider}'s answers; it leaves with {@code owner}'s shutdown.
     *
     * @throws IllegalStateException naming the owner when a kind it answers is already answered by another provider
     */
    public void register(ECPluginData owner, GroupProvider provider) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(provider, "provider");
        synchronized (registered) {
            for (GroupKind kind : provider.getKinds()) {
                for (Entry entry : registered) {
                    for (GroupKind theirs : entry.provider.getKinds()) {
                        if (theirs.getKey().equals(kind.getKey())) {
                            throw new IllegalStateException("Group kind " + kind.getKey() + " is already answered by a GroupProvider of "
                                    + entry.owner.getMetaInfo().getName() + ": one provider per kind - unregister that one first, or"
                                    + " give your kind another key.");
                        }
                    }
                }
            }
            registered.add(new Entry(owner, provider));
        }
    }

    /** Drops every provider {@code owner} registered (and the resolver hook, when it was the one that attached it). */
    public void unregisterAll(ECPluginData owner) {
        registered.removeIf(entry -> entry.owner == owner);
        if (resolverOwner == owner) {
            detachFromResolver();
        }
    }

    /**
     * Internal: registers the ACTOR holder source of the flag resolution under {@code owner} - the core on its enable,
     * {@code GroupWorld} in a test. Once only: a second call while attached does nothing and answers false.
     */
    public synchronized boolean attachToResolver(ECPluginData owner) {
        if (resolverOwner != null) {
            return false;
        }
        resolverOwner = Objects.requireNonNull(owner, "owner");
        ActorHolders.register(owner, new ActorHolders.Provider() {
            @Override
            public Collection<? extends FlagHolder> holdersOf(FPlayer player) {
                return getHoldersOf(player);
            }

            @Override
            public Collection<? extends FlagHolder> all() {
                List<FlagHolder> all = new ArrayList<>();
                for (GroupManager manager : GroupManager.all()) {
                    all.addAll(manager.getAll());
                }
                return all;
            }
        });
        return true;
    }

    /** Internal: undoes {@link #attachToResolver}. */
    public synchronized void detachFromResolver() {
        ECPluginData owner = resolverOwner;
        if (owner != null) {
            resolverOwner = null;
            ActorHolders.unregisterAll(owner);
        }
    }

    /**
     * Every group {@code player} is an explicit member of, provider by provider. A registered provider that throws, here
     * or in any other query, counts as one that does not answer; the core's own managers are not shielded.
     */
    public List<GroupId> getGroupsOf(UUID player) {
        List<GroupId> ids = new ArrayList<>();
        for (GroupManager manager : GroupManager.all()) {
            ids.addAll(manager.getGroupIdsOf(player));
        }
        for (Entry entry : registered) {
            ids.addAll(entry.ask(provider -> provider.getGroupIdsOf(player), Collections.<GroupId>emptyList()));
        }
        return ids;
    }

    public List<GroupId> getGroupsOf(UUID player, GroupKind kind) {
        List<GroupId> ids = new ArrayList<>();
        for (GroupManager manager : GroupManager.all()) {
            ids.addAll(manager.getGroupIdsOf(player, kind));
        }
        for (Entry entry : registered) {
            ids.addAll(entry.ask(provider -> provider.getGroupIdsOf(player, kind), Collections.<GroupId>emptyList()));
        }
        return ids;
    }

    /** The group {@code id} names when the core manages it - a manager's or a region's; null for a provider's or unknown. */
    public @Nullable Group byId(GroupId id) {
        if (id.isRegionLocal()) {
            Region region = RegionLookup.get().byId(id.getRegion());
            return region == null ? null : region.getGroupIfPresent(id.getName());
        }
        for (GroupManager manager : GroupManager.all()) {
            Group group = manager.byId(id);
            if (group != null) {
                return group;
            }
        }
        return null;
    }

    public boolean exists(GroupId id) {
        if (byId(id) != null) {
            return true;
        }
        for (Entry entry : registered) {
            if (entry.ask(provider -> provider.exists(id), false)) {
                return true;
            }
        }
        return false;
    }

    /** The explicit members of {@code id}, offline included; empty when unknown. */
    public Set<UUID> getMembers(GroupId id) {
        Group managed = byId(id);
        if (managed != null) {
            Set<UUID> uuids = new LinkedHashSet<>();
            for (GroupMember member : managed.getMembers()) {
                uuids.add(member.getUuid());
            }
            return uuids;
        }
        for (Entry entry : registered) {
            if (entry.ask(provider -> provider.exists(id), false)) {
                return entry.ask(provider -> provider.getMembers(id), Collections.<UUID>emptySet());
            }
        }
        return Collections.emptySet();
    }

    public @Nullable UUID getLeader(GroupId id) {
        Group managed = byId(id);
        if (managed != null) {
            return managed.getLeader();
        }
        for (Entry entry : registered) {
            if (entry.ask(provider -> provider.exists(id), false)) {
                return entry.ask(provider -> provider.getLeader(id), null);
            }
        }
        return null;
    }

    /** Whether any member of {@code id} is online now. */
    public boolean hasOnlineMember(GroupId id) {
        for (UUID uuid : getMembers(id)) {
            FPlayer player = EverNifeCore.getPlatform().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code a} and {@code b} are explicit members of one group, of any provider and any kind but a region's. */
    public boolean shareGroup(UUID a, UUID b) {
        if (a.equals(b)) {
            return false;
        }
        Set<GroupId> ofA = new HashSet<>(getGroupsOf(a));
        for (GroupId id : getGroupsOf(b)) {
            if (ofA.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** The flag holders that follow {@code player}, highest priority first: the ACTOR layer of the resolution. */
    public List<FlagHolder> getHoldersOf(FPlayer player) {
        List<FlagHolder> holders = new ArrayList<>();
        for (GroupManager manager : GroupManager.all()) {
            holders.addAll(manager.getHoldersOf(player));
        }
        for (Entry entry : registered) {
            holders.addAll(entry.ask(provider -> provider.getHoldersOf(player), Collections.<FlagHolder>emptyList()));
        }
        holders.sort((x, y) -> Integer.compare(y.getPriority(), x.getPriority()));
        return holders;
    }

    /** The groups of every manager whose scope starts with {@code prefix} (the ephemeral groups of an arena). */
    public List<Group> byScope(String prefix) {
        List<Group> found = new ArrayList<>();
        for (GroupManager manager : GroupManager.all()) {
            found.addAll(manager.getByScope(prefix));
        }
        return found;
    }
}
