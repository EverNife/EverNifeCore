package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.notify.IHudAccess.Capability;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import br.com.finalcraft.evernifecore.region.visual.VisualStyle;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pointing a player at a target: shown on the supported channels, refreshed on the ticker, hidden when it should be. */
class WaypointsTest {

    private static final WorldLocPos CAMP = WorldLocPos.of(100.5, 64, 0.5, "world");

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private TestPlatform platform;
    private TestHud hud;
    private final TestFPlayerSender steve = Senders.player("Steve").at("world", 0.5, 64, 0.5);

    private void open(Platforms platforms) {
        TestClock.acquire();
        world = platforms.install();
        platform = world.platform();
        hud = platform.getTestHud();
        Waypoints.get().registerStock(Plugins.fakeCore(dir.resolve("core").toFile()));
    }

    @AfterEach
    void teardown() {
        Plugins.forget(Plugins.CORE);
        world.close();
        TestClock.release();
    }

    private static Waypoint camp(String... channels) {
        return Waypoint.of(WaypointId.of("finalquests", "lumber-camp"), WaypointTarget.at(CAMP)).channels(channels).build();
    }

    private void tick() {
        TestClock.advance(Duration.ofMillis(ECSettings.WAYPOINT_REFRESH_TICKS * 50L));
        TestClock.runDue();
    }

    @Test
    void theCompassPointsAtTheTarget() {
        open(Platforms.lenient());

        WaypointHandle handle = Waypoints.get().show(steve, camp("compass"));

        assertEquals(1, hud.getCompassCalls().size(), hud.getCompassCalls().toString());
        assertEquals("set", hud.getCompassCalls().get(0).action);
        assertEquals(CAMP, hud.getCompassCalls().get(0).extras.get(0));
        assertTrue(handle.isActive());
        assertEquals(Collections.singletonList("evernifecore:compass"), handle.getChannelKeys());
    }

    @Test
    void aTargetThatIsGoneOnRefreshHidesTheWaypoint() {
        open(Platforms.lenient());
        AtomicReference<WorldLocPos> boss = new AtomicReference<>(CAMP);
        Waypoint chase = Waypoint.of(WaypointId.of("finalquests", "boss"), WaypointTarget.following(boss::get)).channels("compass").build();
        WaypointHandle handle = Waypoints.get().show(steve, chase);

        WorldLocPos moved = WorldLocPos.of(120.5, 64, 0.5, "world");
        boss.set(moved);
        tick();
        assertEquals(moved, hud.getCompassCalls().get(1).extras.get(0), "the refresh followed the target");

        boss.set(null);
        tick();

        TestHud.HudCall last = hud.getCompassCalls().get(hud.getCompassCalls().size() - 1);
        assertEquals("reset", last.action);
        assertFalse(handle.isActive());
        assertTrue(Waypoints.get().getActive(steve).isEmpty());
    }

    @Test
    void noSupportedChannelRefusesNamingTheChannelAndThePlatform() {
        open(Platforms.lenient().hudWithout(Capability.COMPASS).outlineRenderer(IOutlineRenderer.NONE));

        WaypointException refused = assertThrows(WaypointException.class, () -> Waypoints.get().show(steve, camp("compass")));

        assertTrue(refused.getMessage().contains("compass"), refused.getMessage());
        assertTrue(refused.getMessage().contains("(" + platform.getPlatformProviderId() + ")"), refused.getMessage());
        assertTrue(hud.getCompassCalls().isEmpty());
        assertTrue(Waypoints.get().getActive(steve).isEmpty());
    }

    @Test
    void theParticlePathDrawsTowardsTheTargetWithThePathStyle() {
        open(Platforms.lenient().recordingOutlines());

        Waypoints.get().show(steve, camp("particle-path"));
        tick();

        assertEquals(2, platform.getRenderedOutlines().size(), "one frame on show, one per refresh");
        for (TestPlatform.RenderedOutline frame : platform.getRenderedOutlines()) {
            assertEquals(VisualStyle.PATH, frame.style);
            assertTrue(frame.points.size() >= 1 && frame.points.size() <= 16, frame.points.toString());
        }
        List<WorldLocPos> points = platform.getRenderedOutlines().get(0).points;
        assertTrue(points.get(points.size() - 1).getX() > points.get(0).getX(), "walking towards the camp, east");
    }

    @Test
    void anUnsupportedChannelIsSkippedWhenAnotherIsSupported() {
        open(Platforms.lenient().hudWithout(Capability.COMPASS).recordingOutlines());

        WaypointHandle handle = Waypoints.get().show(steve, camp("compass", "particle-path"));

        assertEquals(Collections.singletonList("evernifecore:particle-path"), handle.getChannelKeys());
        assertTrue(hud.getCompassCalls().isEmpty());
        assertEquals(1, platform.getRenderedOutlines().size());
    }

    @Test
    void aWaypointWithADurationHidesItselfWhenItEnds() {
        open(Platforms.lenient());
        Waypoint brief = Waypoint.of(WaypointId.of("finalquests", "hint"), WaypointTarget.at(CAMP))
                .channels("compass").duration(Duration.ofSeconds(1)).build();
        Waypoints.get().show(steve, brief);

        tick();
        assertEquals(1, Waypoints.get().getActive(steve).size(), "half a second in, still shown");
        TestClock.advance(Duration.ofSeconds(1));
        TestClock.runDue();

        assertTrue(Waypoints.get().getActive(steve).isEmpty());
        assertEquals("reset", hud.getCompassCalls().get(hud.getCompassCalls().size() - 1).action);
    }

    @Test
    void theSameIdReplacesTheWaypointShownBefore() {
        open(Platforms.lenient());
        WaypointHandle first = Waypoints.get().show(steve, camp("compass"));

        WorldLocPos elsewhere = WorldLocPos.of(-50.5, 70, 3.5, "world");
        Waypoints.get().show(steve, Waypoint.of(WaypointId.of("finalquests", "lumber-camp"), WaypointTarget.at(elsewhere))
                .channels("compass").build());

        assertFalse(first.isActive());
        assertEquals(1, Waypoints.get().getActive(steve).size());
        List<TestHud.HudCall> calls = hud.getCompassCalls();
        assertEquals(elsewhere, calls.get(calls.size() - 1).extras.get(0));
    }

    @Test
    void aPluginLeavingHidesItsWaypointsOnly() {
        open(Platforms.lenient());
        ECPluginData quests = Plugins.fakePluginData("FinalQuests", dir.resolve("quests").toFile());
        Waypoints.get().show(steve, camp("compass"));
        Waypoints.get().show(steve, Waypoint.of(WaypointId.of("finaljobs", "mine"), WaypointTarget.at(CAMP)).channels("compass").build());

        Waypoints.get().hideOwnedBy(quests);

        assertEquals(1, Waypoints.get().getActive(steve).size());
        assertEquals("finaljobs:mine", Waypoints.get().getActive(steve).iterator().next().getId().getKey());
        Plugins.forget("FinalQuests");
    }

    @Test
    void quittingForgetsThePlayersWaypoints() {
        open(Platforms.lenient());
        Waypoints.get().show(steve, camp("compass"));

        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(steve.getUniqueId(), steve.getName())));

        assertTrue(Waypoints.get().getActive(steve).isEmpty());
        assertEquals("reset", hud.getCompassCalls().get(hud.getCompassCalls().size() - 1).action);
    }

    @Test
    void validateRefusesAListWithNoSupportedChannel() {
        open(Platforms.lenient().hudWithout(Capability.COMPASS));
        ECPluginData quests = Plugins.fakePluginData("FinalQuests", dir.resolve("quests").toFile());

        WaypointException refused = assertThrows(WaypointException.class,
                () -> Waypoints.get().validate(quests, "camp", Collections.singletonList("compass")));

        assertTrue(refused.getMessage().contains("finalquests:camp"), refused.getMessage());
        Plugins.forget("FinalQuests");
    }
}
