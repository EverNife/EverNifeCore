package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.storage.Persistence;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Static entry points of the group engine: the ephemeral builder and the network SPI. */
public final class Groups {

    /** The answer while no plugin registered a network: every call fails, teaching how to get one. */
    private static final GroupNetwork REFUSING = new GroupNetwork() {
        @Override
        public CompletableFuture<SendResult> send(Group group, String server) {
            return refused();
        }

        @Override
        public Optional<String> getServer(UUID player) {
            throw new GroupNetworkUnavailableException();
        }

        @Override
        public CompletableFuture<SendResult> follow(Group group) {
            return refused();
        }

        private CompletableFuture<SendResult> refused() {
            CompletableFuture<SendResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new GroupNetworkUnavailableException());
            return failed;
        }
    };

    private static @Nullable ECPluginData networkOwner;
    private static volatile GroupNetwork network = REFUSING;

    private Groups() {
    }

    /**
     * The registered network, or the one whose every call fails with {@link GroupNetworkUnavailableException} (a
     * failed future for {@code send}/{@code follow}, a throw for {@code getServer}). Thread: any.
     */
    public static GroupNetwork network() {
        return network;
    }

    /** Whether a plugin registered a network; false until one does. */
    public static boolean hasNetwork() {
        return network != REFUSING;
    }

    /**
     * Makes {@code impl} the server's network until {@code owner} shuts down.
     *
     * @throws IllegalStateException naming the owner of the network already registered - there is one per server
     */
    public static synchronized void registerNetwork(ECPluginData owner, GroupNetwork impl) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(impl, "impl");
        if (networkOwner != null) {
            throw new IllegalStateException("A GroupNetwork is already registered by " + networkOwner.getMetaInfo().getName()
                    + ": a server has one. Unregister it (Groups.unregisterNetwork) before registering another.");
        }
        networkOwner = owner;
        network = impl;
    }

    /** Takes out the network {@code owner} registered, if any; called by its shutdown. */
    public static synchronized void unregisterNetwork(ECPluginData owner) {
        if (networkOwner == owner) {
            networkOwner = null;
            network = REFUSING;
        }
    }

    /**
     * Builds a TRANSIENT group of {@code owner}, kept by its {@link GroupManager} ({@link GroupManager#of(ECPluginData)},
     * created on first use) and never stored - an arena wave, a dungeon run:
     *
     * <pre>{@code
     * Group wave = Groups.ephemeral(theDuel, TEAM).name("arena-1-wave-7").scope("theduel:arena-1/wave-7")
     *         .roster(Roster.live(inArena))
     *         .onJoin(Edge.gate(entryGate).notify(EdgeAudience.MEMBERS, "&a{player} joined"))
     *         .kickWhen(fellThreeTimes, FancyText.of("&cYou fell 3 times"))
     *         .dissolveWhen(Dissolution.whenEmpty(), Dissolution.after(Duration.ofMinutes(20)))
     *         .create();
     * }</pre>
     *
     * The kind is registered on the manager when it is not yet.
     */
    public static Ephemeral ephemeral(ECPluginData owner, GroupKind kind) {
        return new Ephemeral(GroupManager.of(Objects.requireNonNull(owner, "owner")), Objects.requireNonNull(kind, "kind"));
    }

    /** The builder {@link #ephemeral} returns. One use: {@link #create()} makes one group. */
    public static final class Ephemeral {
        private final GroupManager manager;
        private final GroupKind kind;
        private @Nullable String name;
        private @Nullable String scope;
        private @Nullable Roster roster;
        private @Nullable Edge onJoin;
        private @Nullable Edge onLeave;
        private @Nullable Condition kickWhen;
        private @Nullable FancyText kickReason;
        private QuitPolicy onQuit = QuitPolicy.LEAVE;
        private final List<Dissolution> dissolutions = new ArrayList<>();
        private @Nullable UUID leader;

        private Ephemeral(GroupManager manager, GroupKind kind) {
            this.manager = manager;
            this.kind = kind;
        }

        /** Default {@code e<n>}, unique in the manager. */
        public Ephemeral name(String name) {
            this.name = name;
            return this;
        }

        /** Free text to find the group by later ({@link GroupLookup#byScope}). */
        public Ephemeral scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Ephemeral roster(Roster roster) {
            this.roster = roster;
            return this;
        }

        public Ephemeral onJoin(Edge edge) {
            this.onJoin = edge;
            return this;
        }

        public Ephemeral onLeave(Edge edge) {
            this.onLeave = edge;
            return this;
        }

        /** @see Group#setKickWhen */
        public Ephemeral kickWhen(Condition condition, @Nullable FancyText reason) {
            this.kickWhen = Objects.requireNonNull(condition, "condition");
            this.kickReason = reason;
            return this;
        }

        /** Default LEAVE. */
        public Ephemeral onQuit(QuitPolicy policy) {
            this.onQuit = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Ephemeral dissolveWhen(Dissolution... rules) {
            dissolutions.addAll(Arrays.asList(rules));
            return this;
        }

        /** Joins first and leads. */
        public Ephemeral leader(UUID uuid) {
            this.leader = Objects.requireNonNull(uuid, "uuid");
            return this;
        }

        /**
         * Creates the group (posting {@link br.com.finalcraft.evernifecore.group.event.GroupCreatedEvent}), then fills
         * its roster. Any thread.
         *
         * @throws IllegalStateException as {@link GroupManager#create(String, GroupKind, GroupOptions)} does
         */
        public Group create() {
            if (!manager.getKinds().contains(kind)) {
                manager.registerKind(kind);
            }
            GroupOptions options = GroupOptions.defaults().withPersistence(Persistence.TRANSIENT).withQuitPolicy(onQuit);
            if (leader != null) {
                options = options.withLeader(leader);
            }
            if (onJoin != null) {
                options = options.withJoinEdge(onJoin);
            }
            if (onLeave != null) {
                options = options.withLeaveEdge(onLeave);
            }
            Group group = manager.create(name != null ? name : manager.nextEphemeralName(), kind, options);
            group.setScope(scope);
            group.dissolveWhen(dissolutions);
            if (roster != null) {
                group.setRoster(roster);
            }
            if (kickWhen != null) {
                group.setKickWhen(kickWhen, kickReason);
            }
            return group;
        }
    }
}
