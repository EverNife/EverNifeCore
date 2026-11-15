package br.com.finalcraft.evernifecore.waypoint;

import br.com.finalcraft.evernifecore.config.plugin.WaypointSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each plugin's waypoints: the channels and the particle path length of the profile it declares. */
class WaypointProfileTest {

    //100 blocks east of Steve: far enough that the path length, not the distance, decides the points
    private static final WorldLocPos CAMP = WorldLocPos.of(100.5, 64, 0.5, "world");

    @TempDir
    Path dir;

    private ECoreTestWorld world;
    private TestPlatform platform;
    private ECPluginData quests;
    private ECPluginData jobs;
    private final TestFPlayerSender steve = Senders.player("Steve").at("world", 0.5, 64, 0.5);

    @BeforeEach
    void setup() {
        TestClock.acquire();
        world = Platforms.lenient().recordingOutlines().install();
        platform = world.platform();
        Waypoints.get().registerStock(Plugins.fakeCore(dir.resolve("core").toFile()));
        quests = Plugins.fakeOther("FakeQuests", dir.resolve("quests").toFile());
        jobs = Plugins.fakeOther("FakeJobs", dir.resolve("jobs").toFile());
    }

    @AfterEach
    void teardown() {
        Plugins.forget("FakeQuests");
        Plugins.forget("FakeJobs");
        Plugins.forget(Plugins.CORE);
        world.close();
        TestClock.release();
    }

    private static WaypointSettings settings(String... channels) {
        WaypointSettings settings = new WaypointSettings();
        settings.setChannels(Arrays.asList(channels));
        return settings;
    }

    private static Waypoint camp(ECPluginData owner) {
        return Waypoint.of(WaypointId.of(owner, "camp"), WaypointTarget.at(CAMP)).build();
    }

    @Test
    void aWaypointWithoutChannelsTakesTheChannelsOfItsOwnersProfile() {
        WaypointProfile.register(quests, settings("particle-path"));

        WaypointHandle handle = Waypoints.get().show(steve, camp(quests));

        assertEquals(Collections.singletonList("evernifecore:particle-path"), handle.getChannelKeys());
    }

    @Test
    void aPluginWithoutAProfileTakesTheBuiltinCompassAndParticlePath() {
        WaypointHandle handle = Waypoints.get().show(steve, camp(quests));

        assertEquals(Arrays.asList("evernifecore:compass", "evernifecore:particle-path"), handle.getChannelKeys());
        assertEquals(16, camp(quests).getParticlePathLength());
    }

    @Test
    void twoPluginsWithDifferentProfilesPointThroughDifferentChannels() {
        WaypointProfile.register(quests, settings("compass"));
        WaypointProfile.register(jobs, settings("particle-path"));

        assertEquals(Collections.singletonList("evernifecore:compass"), Waypoints.get().show(steve, camp(quests)).getChannelKeys());
        assertEquals(Collections.singletonList("evernifecore:particle-path"), Waypoints.get().show(steve, camp(jobs)).getChannelKeys());
    }

    @Test
    void theWaypointsPathLengthDecidesHowManyPointsThePathDraws() {
        Waypoints.get().show(steve, Waypoint.of(WaypointId.of(quests, "short"), WaypointTarget.at(CAMP))
                .channels("particle-path").particlePathLength(5).build());

        assertEquals(5, platform.getRenderedOutlines().get(0).points.size());
    }

    @Test
    void aWaypointWithoutAPathLengthTakesTheOneOfItsOwnersProfile() {
        WaypointSettings settings = settings("particle-path");
        settings.setParticlePathLength(3);
        WaypointProfile.register(quests, settings);

        Waypoints.get().show(steve, camp(quests));

        assertEquals(3, platform.getRenderedOutlines().get(0).points.size());
    }

    @Test
    void aMisspelledChannelFailsTheRegisterNamingTheKeyAndTheChannels() {
        WaypointException refused = assertThrows(WaypointException.class, () -> WaypointProfile.register(quests, settings("compas")));

        String message = refused.getMessage();
        assertTrue(message.contains("FakeQuests") && message.contains("waypoints.channels"), message);
        assertTrue(message.contains("'compas'") && message.contains("particle-path"), message);
        assertEquals(WaypointProfile.getBuiltin(), WaypointProfile.of(quests), "a refused profile changes nothing");
    }

    @Test
    void aPluginChannelIsAcceptedBeforeThePluginRegistersIt() {
        WaypointProfile.register(quests, settings("finalquests:marker", "compass"));

        assertEquals(Arrays.asList("finalquests:marker", "compass"), WaypointProfile.of(quests).getChannels());
    }

    @Test
    void registeringAgainReplacesTheProfileAndItLeavesWithThePlugin() {
        WaypointProfile.register(quests, settings("compass"));
        WaypointProfile.register(quests, settings("particle-path"));
        assertEquals(Collections.singletonList("particle-path"), WaypointProfile.of(quests).getChannels());

        ECPluginManager.removePluginData("FakeQuests");

        assertEquals(WaypointProfile.getBuiltin(), WaypointProfile.of(quests), "the removed plugin's profile is still registered");
    }
}
