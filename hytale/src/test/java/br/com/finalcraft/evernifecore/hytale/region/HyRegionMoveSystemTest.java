package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.event.RegionLeaveEvent;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tick that follows region borders on Hytale: asked on a block change only, a denied entry teleported back, a quit forgotten. */
@ECoreTest
class HyRegionMoveSystemTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld server;
    private RegionWorld regions;
    private ECPluginData core;
    private final TestFPlayerSender walker = new TestFPlayerSender("walker");
    private final AtomicInteger asked = new AtomicInteger();
    private final HyRegionMoveSystem system = new HyRegionMoveSystem(() -> {
        asked.incrementAndGet();
        return RegionMovementTracker.get();
    });

    @BeforeEach
    void open() {
        server = Platforms.lenient().install();
        regions = RegionWorld.open("HyMoveOwner", tempDir.resolve("regions"));
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        FlagRegistry.get().register(core, CoreFlags.DENY_ENTRY);
    }

    @AfterEach
    void close() {
        try {
            regions.close();
            FlagRegistry.get().unregisterAll(regions.getOwner());
            FlagRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        } finally {
            server.close();
        }
    }

    private void arena() {
        regions.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10).setFlag(CoreFlags.DENY_ENTRY, Tristate.TRUE);
        assertTrue(EventSourceUsage.get().isNeeded(EventSource.MOVE));
    }

    private WorldLocPos stepTo(double x, double y, double z) {
        return system.step(walker.getUniqueId(), () -> walker, WORLD, new Vector3d(x, y, z), false);
    }

    @Test
    void withNoRegionNeedingMoveNobodyIsFollowed() {
        assertFalse(EventSourceUsage.get().isNeeded(EventSource.MOVE));

        assertNull(stepTo(-5, 5, 5));

        assertEquals(0, asked.get());
        assertFalse(system.isFollowing(walker.getUniqueId()));
    }

    @Test
    void theSameBlockNeverAsksTheTracker() {
        arena();
        assertNull(stepTo(-5.5, 5, 5.5));
        assertEquals(1, asked.get(), "the first tick seeds the player as a join");

        assertNull(stepTo(-5.5, 5, 5.5));
        assertNull(stepTo(-5.1, 5.9, 5.2));   //another spot of the same block, a head turn
        assertEquals(1, asked.get(), "a tick in the same block reached the tracker");

        assertNull(stepTo(-6.5, 5, 5.5));
        assertEquals(2, asked.get());
    }

    @Test
    void aDeniedEntryIsTeleportedBackToTheRevertTo() {
        arena();
        stepTo(-1.5, 5, 5.5);

        WorldLocPos revertTo = stepTo(0.5, 5, 5.5);

        assertNotNull(revertTo, "walking into deny-entry was let through");
        assertTrue(revertTo.getX() < 0, "sent back inside the arena: " + revertTo);
        Teleport teleport = HyRegionMoveSystem.teleport(revertTo, null, new Rotation3f());
        assertEquals(new Vector3d(revertTo.getX(), revertTo.getY(), revertTo.getZ()), teleport.getPosition());

        assertNull(system.step(walker.getUniqueId(), () -> walker, WORLD, new Vector3d(0.5, 5, 5.5), true),
                "while the teleport is pending the player is left alone");
        assertNull(stepTo(-1.5, 5, 5.5), "landing back where he was is no step");
    }

    @Test
    void aQuitForgetsThePlayerAndEndsHisSession() {
        arena();
        List<MoveCause> left = new ArrayList<>();
        ECEventSubscription<RegionLeaveEvent> leaves = ECEventBus.global().subscribe(RegionLeaveEvent.class, event -> left.add(event.getCause()));
        try {
            stepTo(5.5, 5, 5.5);   //logged in already inside
            assertTrue(system.isFollowing(walker.getUniqueId()));

            system.quit(walker.getUniqueId(), () -> walker);

            assertFalse(system.isFollowing(walker.getUniqueId()));
            assertNull(RegionLookup.get().getSessionIfPresent(walker.getUniqueId()));
            assertEquals(1, left.size());
            assertSame(MoveCause.QUIT, left.get(0));

            system.quit(walker.getUniqueId(), () -> walker);
            assertEquals(1, left.size(), "a second quit did something");
        } finally {
            leaves.unsubscribe();
        }
    }
}
