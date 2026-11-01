package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.GroupLookup;
import br.com.finalcraft.evernifecore.group.GroupManager;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import jakarta.annotation.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    private final int quitListenersBefore;
    private final boolean attachedToResolver;
    private volatile GroupManager manager;
    private volatile @Nullable RecordingNotifier notifier;
    private boolean closed;

    private GroupWorld(String ownerName, Path dataFolder) {
        this.quitListenersBefore = quitListeners();
        this.regions = RegionWorld.open(ownerName, dataFolder);
        //the groups' ACTOR values reach the resolution as on a server, where the core attaches it on enable
        this.attachedToResolver = GroupLookup.get().attachToResolver(regions.getOwner());
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

    /**
     * A player joined to nothing, online - and, on a {@link TestPlatform}, in its {@code getOnlinePlayers()} and
     * {@code getPlayer(uuid)} until the world closes; its region session goes then too.
     */
    public TestFPlayerSender player(String name) {
        TestFPlayerSender player = Senders.player(name);
        players.add(player);
        if (EverNifeCore.getPlatform() instanceof TestPlatform) {
            TestPlatform platform = (TestPlatform) EverNifeCore.getPlatform();
            List<FPlayer> online = platform.onlinePlayers == null ? new ArrayList<FPlayer>() : new ArrayList<>(platform.onlinePlayers);
            online.add(player);
            platform.setOnlinePlayers(online);
        }
        return player;
    }

    /** Announces {@code player}'s quit while it is still online, as the platform does, then takes it offline. */
    public void quit(TestFPlayerSender player) {
        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(player.getUniqueId(), player.getName())));
        player.online(false);
        setOnline(player, false);
    }

    /** Puts {@code player} back online and announces its full login. */
    public void login(TestFPlayerSender player) {
        player.online(true);
        setOnline(player, true);
        ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(new PlayerData(player.getUniqueId(), player.getName()), false));
    }

    private static void setOnline(FPlayer player, boolean online) {
        if (EverNifeCore.getPlatform() instanceof TestPlatform) {
            TestPlatform platform = (TestPlatform) EverNifeCore.getPlatform();
            List<FPlayer> now = platform.onlinePlayers == null ? new ArrayList<FPlayer>() : new ArrayList<>(platform.onlinePlayers);
            now.remove(player);
            if (online) {
                now.add(player);
            }
            platform.setOnlinePlayers(now);
        }
    }

    /**
     * The {@link RecordingNotifier} of this world, wired on first call as the {@code chat} channel every default chain
     * ends on: what an edge, a kick or any {@code Notifier.notify} sends lands in it.
     *
     * @throws AssertionError when the core's stock channels are registered already
     */
    public synchronized RecordingNotifier notifier() {
        if (notifier == null) {
            notifier = RecordingNotifier.install(getOwner());
        }
        return notifier;
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

    /**
     * Closes the manager (writing what is pending), then the region world; forgets the players' sessions and takes them
     * off the platform.
     *
     * @throws AssertionError when a quit listener of the group engine is still on the bus - a group with
     *                        {@code QuitPolicy.LEAVE} outlived its manager
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            settle(manager.close());
        } finally {
            if (notifier != null) {
                notifier.uninstall(getOwner());
            }
            if (attachedToResolver) {
                GroupLookup.get().detachFromResolver();
            }
            for (TestFPlayerSender player : players) {
                RegionLookup.get().forgetSession(player.getUniqueId());
            }
            if (EverNifeCore.getPlatform() instanceof TestPlatform) {
                TestPlatform platform = (TestPlatform) EverNifeCore.getPlatform();
                List<FPlayer> online = platform.onlinePlayers == null ? new ArrayList<FPlayer>() : new ArrayList<>(platform.onlinePlayers);
                online.removeAll(players);
                platform.setOnlinePlayers(online);
            }
            regions.close();
        }
        int left = quitListeners() - quitListenersBefore;
        if (left > 0) {
            throw new AssertionError(left + " quit listener(s) of the group engine are still on the bus after the GroupWorld closed:"
                    + " a group with QuitPolicy.LEAVE outlived its manager.");
        }
    }

    private static int quitListeners() {
        return ECEventBus.global().getSubscriptions(ECPlayerQuitEvent.class).size();
    }
}
