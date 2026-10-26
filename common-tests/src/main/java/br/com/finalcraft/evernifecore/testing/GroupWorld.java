package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupManager;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.region.RegionLookup;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One plugin that owns groups (and regions), for a test: a {@link RegionWorld} and a loaded {@link GroupManager} on the
 * same storage, the same plugin and the same {@link TestClock}. Needs the platform a {@link RegionWorld} needs.
 *
 * <pre>{@code
 * try (GroupWorld parties = GroupWorld.open("FakeParty", tempDir)) {
 *     GroupKind party = parties.kind("party", GroupKind.Cardinality.ONE);
 *     Group alpha = parties.group("alpha", party);
 *     alpha.join(parties.player("steve"));
 * }
 * }</pre>
 */
public final class GroupWorld implements AutoCloseable {

    private final RegionWorld regions;
    private final List<TestFPlayerSender> players = new CopyOnWriteArrayList<>();
    private volatile GroupManager manager;
    private boolean closed;

    private GroupWorld(String ownerName, Path dataFolder) {
        this.regions = RegionWorld.open(ownerName, dataFolder);
        this.manager = GroupManager.of(regions.getOwner(), regions.getStorage());
        load();
    }

    /** Opens the groups of a fake plugin named {@code ownerName} whose data folder is {@code dataFolder}, already loaded. */
    public static GroupWorld open(String ownerName, Path dataFolder) {
        return new GroupWorld(ownerName, dataFolder);
    }

    /** The region side: the same owner, the same storage, the same clock. */
    public RegionWorld regions() {
        return regions;
    }

    public GroupManager getManager() {
        return manager;
    }

    public ECPluginData getOwner() {
        return regions.getOwner();
    }

    /** The backend under the owner's storage, for {@code callsTo("save")}. */
    public ScriptedStorage getBackend() {
        return regions.getBackend();
    }

    /** A kind of this owner, registered on the manager. */
    public GroupKind kind(String name, GroupKind.Cardinality cardinality, GroupRole... roles) {
        GroupKind kind = GroupKind.of(getOwner(), name).cardinality(cardinality).roles(roles).build();
        manager.registerKind(kind);
        return kind;
    }

    /** A group created through the manager, with the manager's default persistence. */
    public Group group(String name, GroupKind kind) {
        return manager.create(name, kind);
    }

    /** A player joined to nothing; its region session goes when the world closes. */
    public TestFPlayerSender player(String name) {
        TestFPlayerSender player = Senders.player(name);
        players.add(player);
        return player;
    }

    /**
     * Writes what is pending and closes the manager, then opens a new one of the same owner on the same storage - NOT
     * loaded, so the test registers its kinds first and calls {@link #load()}: what the next server start reads.
     */
    public GroupManager reopen() {
        settle(manager.close());
        manager = GroupManager.of(getOwner(), regions.getStorage());
        return manager;
    }

    /** Loads the current manager, running the scheduler for it. */
    public void load() {
        settle(manager.load());
    }

    /** Writes every pending group change now, running the scheduler for it. */
    public void flush() {
        settle(manager.flush());
    }

    public Instant now() {
        return regions.now();
    }

    /** Moves the clock forward; nothing runs until {@link #runDue()}. */
    public void advance(Duration by) {
        regions.advance(by);
    }

    /** Runs every task whose delay has passed by the clock, once; how many ran. */
    public int runDue() {
        return regions.runDue();
    }

    /** Every event of {@code type} posted on the global bus since the first call, in order; the list is live. */
    public <T extends IECEvent> List<T> recorded(Class<T> type) {
        return regions.recorded(type);
    }

    private void settle(CompletableFuture<Void> future) {
        for (int round = 0; !future.isDone() && round < 100; round++) {
            runDue();
        }
        future.join();
    }

    /** Closes the manager (writing what is pending), then the region world; forgets the players' sessions. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            settle(manager.close());
        } finally {
            for (TestFPlayerSender player : players) {
                RegionLookup.get().forgetSession(player.getUniqueId());
            }
            regions.close();
        }
    }
}
