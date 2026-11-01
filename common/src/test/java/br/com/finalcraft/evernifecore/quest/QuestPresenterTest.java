package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.notify.HudSounds;
import br.com.finalcraft.evernifecore.notify.Notifier;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.RecordingQuestPresenter;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestHud;
import br.com.finalcraft.evernifecore.waypoint.Waypoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The presenter SPI: when the engine calls it, and what the default one shows through the notifier and the waypoints. */
class QuestPresenterTest {

    private static final String WALK = "{id: walk, name: Walk, stages: [{id: s, objectives: [{id: dirt, type: break-block,"
            + " block: \"minecraft:dirt\", goal: 2}]}]}";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private TestHud hud;
    private ECPluginData core;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        hud = world.platform().getTestHud();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        Notifier.get().registerStock(core);
        Waypoints.get().registerStock(core);
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        alex = quests.player("Alex");
        engine.register(quests.parse(WALK));
    }

    @AfterEach
    void close() {
        quests.close();
        Waypoints.get().clearAll();
        Notifier.get().clearAll();
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    private void start(String quest) {
        assertTrue(await(engine.start(quests.owner(alex), quests.id(quest), QuestParams.NONE)).isSuccess());
    }

    @Test
    void theEngineCallsThePresenterAtEachMoment() {
        RecordingQuestPresenter recording = new RecordingQuestPresenter();
        engine.setPresenter(recording);
        start("walk");
        await(engine.track(alex, quests.id("walk")));
        quests.post(quests.breakBlock(alex, "minecraft:dirt"));
        quests.post(quests.breakBlock(alex, "minecraft:dirt"));
        await(engine.track(alex, null));

        assertEquals(Arrays.asList("track Alex fakequests:walk", "progress Alex fakequests:walk/dirt 1", "objective Alex fakequests:walk/dirt",
                "stage Alex fakequests:walk/s", "complete Alex fakequests:walk", "untrack Alex fakequests:walk"), recording.getLines());
    }

    @Test
    void theDefaultShowsProgressOnTheActionBarCompletionAsTitleAndSoundAndTheTrackedQuestOnTheBossBar() {
        start("walk");
        await(engine.track(alex, quests.id("walk")));
        assertFalse(hud.getBossBarCalls().isEmpty(), "the tracked quest: " + hud.getBossBarCalls());

        quests.post(quests.breakBlock(alex, "minecraft:dirt"));
        assertFalse(hud.getActionBarCalls().isEmpty(), "progress: " + hud.getActionBarCalls());

        quests.post(quests.breakBlock(alex, "minecraft:dirt"));
        assertFalse(hud.getTitleCalls().isEmpty(), "completion title: " + hud.getTitleCalls());
        assertTrue(hud.getSoundCalls().stream().anyMatch(call -> HudSounds.LEVEL_UP.equals(call.text)), "completion sound: " + hud.getSoundCalls());
    }

    @Test
    void theTrackedQuestPointsAtItsTargetUntilTheObjectiveCloses() {
        engine.register(quests.parse("{id: go, stages: [{id: s, objectives: [{id: there, type: reach-location, world: world,"
                + " x: 100, y: 64, z: 100, radius: 2}]}]}"));
        quests.move(alex, WorldLocPos.of(0, 64, 0, "world"));
        start("go");

        await(engine.track(alex, quests.id("go")));
        assertEquals(1, Waypoints.get().getActive(alex).size(), "a waypoint at the reach-location target");

        quests.move(alex, WorldLocPos.of(100, 64, 100, "world"));
        assertTrue(Waypoints.get().getActive(alex).isEmpty(), "reached: the waypoint went with the objective");
    }
}
