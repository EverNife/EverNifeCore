package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscriber;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everydatabase.Storages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The world every manager test starts from: two plugins, each with its own counting in-memory storage, the global
 * index empty, and every manager, flag, clock and schedule put back afterwards.
 */
@ECoreTest
abstract class ManagedRegionFixture {

    static final String OWNER = "RegionOwner";
    static final String OTHER = "RegionOther";
    static final String WORLD = "world";

    @TempDir
    Path tempDir;

    ECPluginData owner;
    ECPluginData other;
    ScriptedStorage backend;
    ECStorage storage;
    ECStorage otherStorage;

    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final List<ECStorage> storages = new ArrayList<>();

    @BeforeEach
    void openWorld() {
        owner = Plugins.fakePluginData(OWNER, tempDir.resolve("owner").toFile());
        other = Plugins.fakePluginData(OTHER, tempDir.resolve("other").toFile());
        backend = ScriptedStorage.wrapping(Storages.createInMemory());
        backend.init().join();
        storage = track(backend.asECStorage());
        ScriptedStorage otherBackend = ScriptedStorage.wrapping(Storages.createInMemory());
        otherBackend.init().join();
        otherStorage = track(otherBackend.asECStorage());
    }

    @AfterEach
    void closeWorld() {
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        RegionScheduler.set(null);   //first: a held agenda would hold the closing writes for the whole 30 s timeout
        RegionManager.closeAll();
        RegionManager.setFlushDelayMillis(RegionManager.DEFAULT_FLUSH_DELAY_MILLIS);
        RegionManager.setRetryDelayMillis(RegionManager.DEFAULT_RETRY_DELAY_MILLIS);
        TestRegions.restoreClock();
        FlagRegistry.get().unregisterAll(owner);
        FlagRegistry.get().unregisterAll(other);
        for (ECStorage open : storages) {
            open.close().join();
        }
        Plugins.forget(OWNER);
        Plugins.forget(OTHER);
    }

    ECStorage track(ECStorage opened) {
        storages.add(opened);
        return opened;
    }

    RegionManager manager() {
        return RegionManager.of(owner, storage);
    }

    /** Every event of {@code type} posted from now on, in order. */
    <T extends IECEvent> List<T> record(Class<T> type) {
        List<T> seen = new CopyOnWriteArrayList<>();
        onEvent(type, seen::add);
        return seen;
    }

    /** Subscribes for the length of the test. */
    <T extends IECEvent> void onEvent(Class<T> type, ECEventSubscriber<? super T> subscriber) {
        subscriptions.add(ECEventBus.global().subscribe(type, subscriber));
    }

    static CuboidShape box(int x1, int z1, int x2, int z2) {
        return CuboidShape.of(BlockPos.of(x1, 0, z1), BlockPos.of(x2, 100, z2));
    }

    /** Settings of {@link RoomFlag}, with an equality a round trip can assert on. */
    public static class RoomSettings extends FlagSettings {
        private String greeting = "hello";

        public String getGreeting() {
            return greeting;
        }

        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RoomSettings && greeting.equals(((RoomSettings) other).greeting)
                    && getTriggerInterval().equals(((RoomSettings) other).getTriggerInterval());
        }

        @Override
        public int hashCode() {
            return greeting.hashCode();
        }
    }

    /** A tristate flag with settings of its own. */
    static final class RoomFlag extends TristateFlag {
        RoomFlag(ECPluginData owner) {
            super(FlagKey.of(owner, "room"), EnumSet.of(EventSource.MOVE), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Class<? extends FlagSettings> getSettingsType() {
            return RoomSettings.class;
        }
    }

    /** A {@link RoomFlag} of {@code by}, registered. */
    static RoomFlag registeredRoomFlag(ECPluginData by) {
        RoomFlag flag = new RoomFlag(by);
        FlagRegistry.get().register(by, flag);
        return flag;
    }

    /** Creates and asserts it was created. */
    static Region created(CreationResult result) {
        if (!result.isCreated()) {
            throw new AssertionError("expected a region, got " + result);
        }
        return result.getRegion();
    }
}
