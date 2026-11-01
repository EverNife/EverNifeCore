package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.BreakActionEvent;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.playerdata.PDSectionConfiguration;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.storage.SectionLifecycle;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.quest.event.ObjectiveProgressPreEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestCompleteEvent;
import br.com.finalcraft.evernifecore.quest.event.QuestStartEvent;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.QuestDefinitionsFixture;
import br.com.finalcraft.evernifecore.testing.QuestWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static br.com.finalcraft.evernifecore.testing.QuestWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The engine over a real PlayerData layer and storage (QuestWorld): transitions, the index, state objectives, versions, owners' lifecycle. */
class QuestEngineTest {

    private static final String MINER = "{id: miner, stages: [{id: mine, objectives: [{id: ore, type: break-block, block: 'minecraft:*_ore', goal: 3}]}]}";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private QuestWorld quests;
    private QuestEngine engine;
    private TestFPlayerSender alex;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        quests = QuestWorld.open("FakeQuests", tempDir.resolve("quests"));
        engine = quests.getEngine();
        alex = quests.player("Alex");
    }

    @AfterEach
    void close() {
        quests.close();
        world.close();
    }

    private ProgressOwner alexOwner() {
        return quests.owner(alex);
    }

    private QuestState stateOf(TestFPlayerSender player, String quest) {
        return engine.getState(quests.owner(player), quests.id(quest)).orElseThrow(() -> new AssertionError("no state of " + quest));
    }

    private void start(TestFPlayerSender player, String quest) {
        StartResult result = await(engine.start(quests.owner(player), quests.id(quest), QuestParams.NONE));
        assertTrue(result.isSuccess(), result.toString());
    }

    // ------------------------------------------------------------------
    //  Graph
    // ------------------------------------------------------------------

    @Test
    void aLinearQuestWalksItsStagesToCompletion() {
        engine.register(quests.parse("{id: linear, stages: ["
                + "{id: mine, objectives: [{id: ore, type: break-block, block: 'minecraft:iron_ore', goal: 2}]},"
                + "{id: hunt, objectives: [{id: z, type: kill, entity: 'minecraft:zombie', goal: 1}]}]}"));
        List<QuestCompleteEvent> completed = quests.recorded(QuestCompleteEvent.class);
        start(alex, "linear");

        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        quests.post(quests.breakBlock(alex, "minecraft:stone"));
        assertEquals(1, stateOf(alex, "linear").getCount("ore"), "stone is not iron ore");
        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        assertEquals("hunt", stateOf(alex, "linear").getStageId());

        quests.post(quests.kill(alex, "minecraft:zombie"));

        QuestState done = stateOf(alex, "linear");
        assertEquals(QuestStatus.COMPLETED, done.getStatus());
        assertEquals(1, done.getTimes());
        assertEquals(1, completed.size());
    }

    @Test
    void anyGroupFollowsTheThenOfTheObjectiveThatClosedIt() {
        engine.register(quests.parse("{id: fork, stages: ["
                + "{id: choose, objectives: {mode: any, items: ["
                + "  {id: north, type: break-block, block: 'minecraft:stone', then: north-path},"
                + "  {id: south, type: kill, entity: 'minecraft:zombie', then: south-path}]}},"
                + "{id: north-path, objectives: [{id: n, type: break-block, block: 'minecraft:dirt'}], then: COMPLETE},"
                + "{id: south-path, objectives: [{id: s, type: kill, entity: 'minecraft:skeleton'}]}]}"));
        start(alex, "fork");

        quests.post(quests.kill(alex, "minecraft:zombie"));

        assertEquals("south-path", stateOf(alex, "fork").getStageId());
        quests.post(quests.breakBlock(alex, "minecraft:dirt"));
        assertEquals("south-path", stateOf(alex, "fork").getStageId(), "the north path's objective is not active");
        quests.post(quests.kill(alex, "minecraft:skeleton"));
        assertEquals(QuestStatus.COMPLETED, stateOf(alex, "fork").getStatus());
    }

    @Test
    void aStateObjectiveIsReevaluatedOnlyAfterTheSourcesItsConditionNeeds() {
        AtomicBoolean rich = new AtomicBoolean();
        Conditions.registry().register(quests.getOwner(), TypeDescriptor.<Condition>of("fakequests:rich")
                .describe("Whether the test made the player rich.")
                .factory(params -> new Condition() {
                    @Override
                    public ConditionResult test(ExecutionContext ctx) {
                        return ConditionResult.of(rich.get(), FancyText.of("not rich"));
                    }

                    @Override
                    public Set<EventSource> getNeeds() {
                        return Collections.singleton(EventSource.ITEM_PICKUP);
                    }
                })
                .build());
        engine.register(quests.parse("{id: hoard, stages: [{id: have, objectives: [{id: rich, type: state, condition: {type: 'fakequests:rich'}}]}]}"));
        start(alex, "hoard");
        rich.set(true);

        quests.post(quests.breakBlock(alex, "minecraft:stone"));
        assertEquals(QuestStatus.ACTIVE, stateOf(alex, "hoard").getStatus(), "BREAK is not a source the condition needs");
        assertFalse(ECEventBus.global().hasListeners(BreakActionEvent.Post.class));

        quests.post(quests.pickup(alex, "minecraft:gold_ingot", 1));
        assertEquals(QuestStatus.COMPLETED, stateOf(alex, "hoard").getStatus(), "a pickup re-evaluates the condition");
    }

    @Test
    void aTimerIsCheckedByTheSweep() {
        engine.register(quests.parse("{id: wait, stages: [{id: w, objectives: [{id: t, type: timer, duration: 2m}]}]}"));
        start(alex, "wait");

        quests.advance(Duration.ofMinutes(1));
        quests.runDue();
        assertEquals(QuestStatus.ACTIVE, stateOf(alex, "wait").getStatus());
        quests.advance(Duration.ofMinutes(1));
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, stateOf(alex, "wait").getStatus());
    }

    @Test
    void aQuestCompletedObjectiveClosesOnceTheOtherQuestCompletes() {
        engine.register(quests.parse(MINER));
        engine.register(quests.parse("{id: after, stages: [{id: a, objectives: [{id: q, type: quest-completed, quest: miner}]}]}"));
        start(alex, "after");
        start(alex, "miner");

        for (int i = 0; i < 3; i++) {
            quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        }
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, stateOf(alex, "after").getStatus());
    }

    // ------------------------------------------------------------------
    //  Progress modes and plugin types
    // ------------------------------------------------------------------

    @Test
    void eachMatchAppliesItsEffectsWithTheDeltaAListenerChanged() {
        List<String> deltas = new CopyOnWriteArrayList<>();
        Effects.registry().register(quests.getOwner(), TypeDescriptor.<Effect>of("fakequests:record-delta")
                .describe("Records the delta.")
                .factory(params -> ctx -> deltas.add(ctx.resolve("{delta}")))
                .build());
        engine.register(quests.parse("{id: job, stages: [{id: work, objectives: [{id: pay, type: break-block, mode: each-match,"
                + " on-match: [{type: 'fakequests:record-delta'}]}]}]}"));
        start(alex, "job");
        List<Integer> seen = new ArrayList<>();
        ECEventSubscription<ObjectiveProgressPreEvent> listener = ECEventBus.global().subscribe(ObjectiveProgressPreEvent.class, event -> {
            seen.add(seen.size());
            if (seen.size() == 1) {
                event.setDelta(5);
            } else {
                event.setCancelled(true);
            }
        });
        try {
            quests.post(quests.breakBlock(alex, "minecraft:stone"));
            quests.post(quests.breakBlock(alex, "minecraft:stone"));
        } finally {
            listener.unsubscribe();
        }

        assertEquals(Collections.singletonList("5"), deltas, "the first match pays the changed delta, the cancelled one pays nothing");
        QuestState state = stateOf(alex, "job");
        assertEquals(QuestStatus.ACTIVE, state.getStatus(), "each-match never completes");
        assertEquals(5, state.getCount("pay"));
    }

    @Test
    void aCancelledProgressPreEventCountsNothing() {
        engine.register(quests.parse(MINER));
        start(alex, "miner");
        ECEventSubscription<ObjectiveProgressPreEvent> listener = ECEventBus.global().subscribe(ObjectiveProgressPreEvent.class,
                event -> event.setCancelled(true));
        try {
            quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        } finally {
            listener.unsubscribe();
        }
        assertEquals(0, stateOf(alex, "miner").getCount("ore"));
    }

    @Test
    void aPluginTypeOnItsOwnSourceCancelsNativeWhenTheMatchCompletes() {
        EventSource capture = EventSource.register(quests.getOwner(), "pokemon-capture");
        ObjectiveTypes.register(quests.getOwner(), new CaptureType(capture, true));
        engine.register(quests.parse("{id: catcher, stages: [{id: c, objectives: [{id: pika, type: 'fakequests:capture', species: pikachu, goal: 2}]}]}"));
        start(alex, "catcher");

        CapturePre first = ECEventBus.global().post(new CapturePre(capture, captureOf(capture, "pikachu")));
        CapturePre other = ECEventBus.global().post(new CapturePre(capture, captureOf(capture, "eevee")));
        CapturePre second = ECEventBus.global().post(new CapturePre(capture, captureOf(capture, "pikachu")));

        assertFalse(first.isCancelled(), "the first catch does not complete the objective");
        assertFalse(other.isCancelled());
        assertTrue(second.isCancelled(), "the catch that completes the objective cancels the native outcome");
        assertEquals(QuestStatus.COMPLETED, stateOf(alex, "catcher").getStatus());
    }

    @Test
    void aTypeThatCancelsNativeOnASourceWithoutPreFailsAtItsRegister() {
        EventSource capture = EventSource.register(quests.getOwner(), "pokemon-capture");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ObjectiveTypes.register(quests.getOwner(), new CaptureType(capture, true) {
                    @Override
                    public Class<? extends ActionEvent<?>> getPreEvent(EventSource source) {
                        return null;
                    }
                }));
        assertTrue(refused.getMessage().contains("fakequests:capture") && refused.getMessage().contains("fakequests:pokemon-capture"),
                refused.getMessage());
    }

    @Test
    void aParamDrawnAtStartIsFrozenInTheStateAndInTheFilter() {
        ECPluginData poke = Plugins.fakePluginData("PokeQuest", tempDir.resolve("poke").toFile());
        try {
            EventSource capture = EventSource.register(poke, "pokemon-capture");
            ObjectiveTypes.register(poke, new CaptureType(capture, false, "pokequest:capture", "species-type"));
            engine.register(QuestDefinitionsFixture.load(quests.getOwner(), "lab-catch"));

            start(alex, "lab-catch");
            String drawn = stateOf(alex, "lab-catch").getParams().get("type");
            assertTrue(Arrays.asList("fire", "water", "grass", "electric").contains(drawn), drawn);
            await(engine.reset(alexOwner(), quests.id("lab-catch")));

            assertTrue(await(engine.start(alexOwner(), quests.id("lab-catch"), QuestParams.of("type", "water"))).isSuccess());
            ECEventBus.global().post(new CapturePost(capture, captureOf(capture, "fire")));
            assertEquals(0, stateOf(alex, "lab-catch").getCount("c"), "a fire catch does not count for the water instance");
            ECEventBus.global().post(new CapturePost(capture, captureOf(capture, "water")));
            assertEquals(1, stateOf(alex, "lab-catch").getCount("c"));
            assertEquals("water", stateOf(alex, "lab-catch").getParams().get("type"));
        } finally {
            Plugins.forget("PokeQuest");
        }
    }

    // ------------------------------------------------------------------
    //  Versions and orphans
    // ------------------------------------------------------------------

    @Test
    void keepByIdKeepsTheProgressOfObjectivesThatStillExist() {
        engine.register(quests.parse("{id: v, version: 1, stages: [{id: s, objectives: ["
                + "{id: ore, type: break-block, block: 'minecraft:iron_ore', goal: 5}, {id: wood, type: break-block, block: 'minecraft:oak_log', goal: 5}]}]}"));
        start(alex, "v");
        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        quests.post(quests.breakBlock(alex, "minecraft:oak_log"));

        engine.register(quests.parse("{id: v, version: 2, on-version-change: keep-by-id, stages: [{id: s, objectives: ["
                + "{id: ore, type: break-block, block: 'minecraft:iron_ore', goal: 5}, {id: rock, type: break-block, block: 'minecraft:stone', goal: 5}]}]}"));

        QuestState state = stateOf(alex, "v");
        assertEquals(QuestStatus.ACTIVE, state.getStatus());
        assertEquals(2, state.getDefinitionVersion());
        assertEquals(1, state.getCount("ore"), "ore still exists");
        assertFalse(state.getObjectives().containsKey("wood"), "wood is gone from the definition");
    }

    @Test
    void resetSendsOpenInstancesBackToAvailableKeepingTheTimes() {
        engine.register(quests.parse("{id: r, version: 1, repeat: {limit: 5}, stages: [{id: s, objectives: [{id: ore, type: break-block, goal: 1}]}]}"));
        start(alex, "r");
        quests.post(quests.breakBlock(alex, "minecraft:stone"));
        start(alex, "r");

        engine.register(quests.parse("{id: r, version: 2, on-version-change: reset, repeat: {limit: 5}, stages: [{id: s, objectives: [{id: ore, type: break-block, goal: 3}]}]}"));

        QuestState state = stateOf(alex, "r");
        assertEquals(QuestStatus.AVAILABLE, state.getStatus());
        assertEquals(1, state.getTimes());
        assertTrue(state.getObjectives().isEmpty());
    }

    @Test
    void anOrphanStateSurvivesOtherRegistersAndAReloadAndLeavesOnlyByReset() {
        engine.register(quests.parse(MINER));
        start(alex, "miner");
        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        engine.unregister(quests.id("miner"));

        engine.register(quests.parse("{id: other, stages: [{id: s, objectives: [{id: o, type: kill, goal: 1}]}]}"));
        quests.quit(alex);
        quests.login(alex);

        assertEquals(Collections.singletonList("fakequests:miner"), new ArrayList<>(engine.getOrphans(alexOwner())));
        assertEquals(1, stateOf(alex, "miner").getCount("ore"), "the orphan is kept as it was");
        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        assertEquals(1, stateOf(alex, "miner").getCount("ore"), "an orphan does not count");

        await(engine.reset(alexOwner(), quests.id("miner")));
        assertTrue(engine.getOrphans(alexOwner()).isEmpty());
        assertFalse(engine.getState(alexOwner(), quests.id("miner")).isPresent());
    }

    // ------------------------------------------------------------------
    //  Repeat
    // ------------------------------------------------------------------

    @Test
    void aCompletedQuestWithACooldownIsAvailableAgainOnceItEnds() {
        engine.register(quests.parse("{id: daily, repeat: {cooldown: 1h}, stages: [{id: s, objectives: [{id: o, type: break-block, goal: 1}]}]}"));
        start(alex, "daily");
        quests.post(quests.breakBlock(alex, "minecraft:stone"));
        assertEquals(QuestStatus.COOLDOWN, engine.getStatus(alexOwner(), quests.id("daily")));
        assertFalse(await(engine.start(alexOwner(), quests.id("daily"), QuestParams.NONE)).isSuccess());

        quests.advance(Duration.ofHours(1));

        assertEquals(QuestStatus.AVAILABLE, engine.getStatus(alexOwner(), quests.id("daily")));
        start(alex, "daily");
        assertEquals(1, stateOf(alex, "daily").getTimes());
    }

    @Test
    void aQuestRequiringAnotherIsLockedUntilTheOtherCompletes() {
        engine.register(quests.parse("{id: tutorial, stages: [{id: s, objectives: [{id: o, type: break-block, goal: 1}]}]}"));
        engine.register(quests.parse("{id: next, requires: [tutorial], stages: [{id: s, objectives: [{id: o, type: kill, goal: 1}]}]}"));

        assertEquals(QuestStatus.LOCKED, engine.getStatus(alexOwner(), quests.id("next")));
        StartResult refused = await(engine.start(alexOwner(), quests.id("next"), QuestParams.NONE));
        assertTrue(refused.getWhy().contains("fakequests:tutorial"), refused.toString());

        start(alex, "tutorial");
        quests.post(quests.breakBlock(alex, "minecraft:stone"));
        assertEquals(QuestStatus.AVAILABLE, engine.getStatus(alexOwner(), quests.id("next")));
    }

    @Test
    void abandoningMovesAnActiveQuestToAbandonedAndStopsCounting() {
        engine.register(quests.parse(MINER));
        start(alex, "miner");
        await(engine.abandon(alexOwner(), quests.id("miner")));

        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));

        assertEquals(QuestStatus.ABANDONED, stateOf(alex, "miner").getStatus());
        assertEquals(0, stateOf(alex, "miner").getCount("ore"));
    }

    // ------------------------------------------------------------------
    //  Index and sources
    // ------------------------------------------------------------------

    @Test
    void actionWithSectionNotLoadedCountsNothing() {
        engine.register(quests.parse("{id: global-miner, owner: global, stages: [{id: s, objectives: [{id: ore, type: break-block, goal: 5}]}]}"));
        engine.register(quests.parse(MINER));
        TestFPlayerSender ghost = quests.logic().player("Ghost"); //online, never logged in: no quest section loaded

        StartResult refused = await(engine.start(quests.owner(ghost), quests.id("miner"), QuestParams.NONE));
        quests.post(quests.breakBlock(ghost, "minecraft:iron_ore"));

        assertEquals(StartResult.Outcome.REFUSED, refused.getOutcome());
        assertTrue(refused.getWhy().contains("not loaded"), refused.getWhy());
        assertFalse(engine.getState(quests.owner(ghost), quests.id("miner")).isPresent());
        assertTrue(engine.getStates(quests.owner(ghost)).isEmpty());
    }

    @Test
    void theIndexOffersAnActionOnlyToTheOwnerItCountsFor() {
        engine.register(quests.parse(MINER));
        engine.register(quests.parse("{id: hunter, stages: [{id: s, objectives: [{id: z, type: kill, entity: 'minecraft:zombie', goal: 3}]}]}"));
        TestFPlayerSender bea = quests.player("Bea");
        start(alex, "miner");
        start(bea, "hunter");

        assertEquals(1, quests.getIndex().getEntries(EventSource.BREAK, alexOwner()).size());
        assertTrue(quests.getIndex().getEntries(EventSource.BREAK, quests.owner(bea)).isEmpty());
        assertEquals(1, quests.getIndex().getEntries(EventSource.KILL, quests.owner(bea)).size());

        quests.post(quests.breakBlock(bea, "minecraft:iron_ore"));
        assertEquals(0, stateOf(alex, "miner").getCount("ore"), "Bea's break is not Alex's");
        quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        assertEquals(1, stateOf(alex, "miner").getCount("ore"));
        assertEquals(0, stateOf(bea, "hunter").getCount("z"));
    }

    @Test
    void theSourceIsListenedOnlyWhileAnObjectiveNeedsIt() {
        engine.register(quests.parse(MINER));
        assertFalse(ECEventBus.global().hasListeners(BreakActionEvent.Post.class), "a registered quest nobody does needs no source");

        start(alex, "miner");
        assertTrue(ECEventBus.global().hasListeners(BreakActionEvent.Post.class));
        assertTrue(quests.getIndex().getListenedSources().contains(EventSource.BREAK));

        for (int i = 0; i < 3; i++) {
            quests.post(quests.breakBlock(alex, "minecraft:iron_ore"));
        }
        quests.advance(Duration.ofMinutes(5));
        quests.runDue();

        assertEquals(QuestStatus.COMPLETED, stateOf(alex, "miner").getStatus());
        assertFalse(ECEventBus.global().hasListeners(BreakActionEvent.Post.class), "the last objective of BREAK left");
        assertFalse(quests.getIndex().getListenedSources().contains(EventSource.BREAK));
    }

    @Test
    void anObjectiveOnASourceThisPlatformNeverRaisesFailsNamingObjectiveSourceAndPlatform() {
        ActionSupport.declare("Hytale 0.7.5", Collections.singleton(EventSource.BREAK), null, false, null);
        try {
            QuestDefinition fisher = quests.parse("{id: fisher, stages: [{id: s, objectives: [{id: lake, type: fish, goal: 3}]}]}");
            RuntimeException refused = assertThrows(RuntimeException.class, () -> engine.register(fisher));
            assertTrue(refused.getMessage().contains("lake") && refused.getMessage().contains("evernifecore:fish")
                    && refused.getMessage().contains("Hytale 0.7.5"), refused.getMessage());
            assertNull(engine.get(quests.id("fisher")));

            engine.register(quests.parse("{id: fisher, stages: [{id: s, objectives: ["
                    + "{id: lake, type: fish, goal: 3, platforms: [minecraft]}, {id: ore, type: break-block, goal: 1}]}]}"));
            assertTrue(engine.get(quests.id("fisher")) != null, "skipped on this platform, the objective no longer refuses");
        } finally {
            ActionSupport.declare("an undeclared platform", null, null, true, null);
        }
    }

    @Test
    void requiresInACycleFailsNamingTheChain() {
        engine.register(quests.parse("{id: a, requires: [b], stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class,
                () -> engine.register(quests.parse("{id: b, requires: [a], stages: [{id: s, objectives: [{id: o, type: kill}]}]}")));
        assertTrue(cycle.getMessage().contains("fakequests:a") && cycle.getMessage().contains("fakequests:b")
                && cycle.getMessage().contains("cycle"), cycle.getMessage());
        assertNull(engine.get(quests.id("b")));
    }

    @Test
    void aQuestStatusCycleInGatesAnswersLockedInsteadOfOverflowing() {
        engine.register(quests.parse("{id: a, gate: {require: [{type: quest-status, quest: b, is: AVAILABLE}]},"
                + " stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));
        engine.register(quests.parse("{id: b, gate: {require: [{type: quest-status, quest: a, is: AVAILABLE}]},"
                + " stages: [{id: s, objectives: [{id: o, type: kill}]}]}"));

        for (int round = 0; round < 2; round++) { //the guard is left clean after a loop
            assertEquals(QuestStatus.LOCKED, engine.getStatus(alexOwner(), quests.id("a")));
            assertEquals(QuestStatus.LOCKED, engine.getStatus(alexOwner(), quests.id("b")));
        }
        StartResult started = await(engine.start(alexOwner(), quests.id("a"), QuestParams.NONE));
        assertEquals(StartResult.Reason.GATE, started.getReason(), started.toString());
    }

    /** The section class of {@link #ofTakesThePluginsSectionConfigurationInOneRegistration}'s own plugin. */
    public static final class ConfiguredQuestSection extends QuestSection {
        public ConfiguredQuestSection() {
            //Jackson
        }
    }

    @Test
    void ofTakesThePluginsSectionConfigurationInOneRegistration() {
        ECPluginData other = Plugins.fakePluginData("ConfiguredQuests", tempDir.resolve("configured").toFile());
        List<QuestEngine> configured = new ArrayList<>();
        ScriptedStorage backend = ScriptedStorage.inMemory();
        backend.init().join();
        ECStorage storage = backend.asECStorage();
        try {
            QuestEngine opened = QuestEngine.of(other, storage, ConfiguredQuestSection.class, (engine, section) -> {
                configured.add(engine);
                section.description("legacy quests").lifecycle(SectionLifecycle.LAZY);
            });
            QuestEngine.of(other, storage, ConfiguredQuestSection.class, (engine, section) -> configured.add(engine));

            assertEquals(Collections.singletonList(opened), configured, "configured once, by the call that registered the section");
            PDSectionConfiguration<?> registered = PlayerController.getConfiguredPDSections().get(ConfiguredQuestSection.class);
            assertEquals("legacy quests", registered.getDescription());
            assertEquals(SectionLifecycle.ONLINE, registered.getLifecycle(), "the engine reads the section while the player is online");
        } finally {
            QuestEngine.closeOwnedBy(other);
            Plugins.forget("ConfiguredQuests");
        }
    }

    @Test
    void aQuestOfAnotherPluginIsRefused() {
        ECPluginData other = Plugins.fakePluginData("OtherQuests", tempDir.resolve("other").toFile());
        try {
            QuestDefinition foreign = QuestDefinitions.parseYaml(other,
                    "{id: x, stages: [{id: s, objectives: [{id: o, type: kill}]}]}");
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> engine.register(foreign));
            assertTrue(refused.getMessage().contains("otherquests:x"), refused.getMessage());
        } finally {
            Plugins.forget("OtherQuests");
        }
    }

    // ------------------------------------------------------------------
    //  Threads
    // ------------------------------------------------------------------

    @Test
    void twoThreadsProgressingOneOwnerLoseNothing() throws Exception {
        engine.register(quests.parse("{id: grind, stages: [{id: s, objectives: [{id: o, type: break-block, goal: 5000}]}]}"));
        start(alex, "grind");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                runs.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < 500; i++) {
                        engine.progress(alexOwner(), quests.id("grind"), "o", 1).join();
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> run : runs) {
                run.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1000, stateOf(alex, "grind").getCount("o"));
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    @Test
    void closeOwnedByWritesTheStatesOfOwnersThatAreNotPlayers() {
        engine.register(quests.parse("{id: server-goal, owner: global, stages: [{id: s, objectives: [{id: o, type: break-block, goal: 10}]}]}"));
        assertTrue(await(engine.start(ProgressOwner.global(), quests.id("server-goal"), QuestParams.NONE)).isSuccess());
        await(engine.progress(ProgressOwner.global(), quests.id("server-goal"), "o", 4));
        int before = quests.getBackend().callsTo("save");

        QuestEngine.closeOwnedBy(quests.getOwner());

        assertTrue(quests.getBackend().callsTo("save") > before, "the global state was written on close");
        assertNull(QuestEngine.ofIfPresent(quests.getOwner()));
        assertTrue(engine.isClosed());
    }

    @Test
    void aReopenedEngineReadsTheNonPlayerStatesBack() {
        engine.register(quests.parse("{id: server-goal, owner: global, stages: [{id: s, objectives: [{id: o, type: break-block, goal: 10}]}]}"));
        await(engine.start(ProgressOwner.global(), quests.id("server-goal"), QuestParams.NONE));
        await(engine.progress(ProgressOwner.global(), quests.id("server-goal"), "o", 4));
        QuestEngine.closeOwnedBy(quests.getOwner());

        QuestEngine reopened = QuestEngine.of(quests.getOwner(), quests.getStorage(), QuestWorld.TestQuestSection.class);
        await(reopened.load());

        assertEquals(4, reopened.getState(ProgressOwner.global(), quests.id("server-goal")).get().getCount("o"));
    }

    @Test
    void discardOwnedByWritesNothing() {
        engine.register(quests.parse("{id: server-goal, owner: global, stages: [{id: s, objectives: [{id: o, type: break-block, goal: 10}]}]}"));
        await(engine.start(ProgressOwner.global(), quests.id("server-goal"), QuestParams.NONE));
        await(engine.progress(ProgressOwner.global(), quests.id("server-goal"), "o", 4));
        int before = quests.getBackend().callsTo("save");

        QuestEngine.discardOwnedBy(quests.getOwner());
        quests.advance(Duration.ofSeconds(5));
        quests.runDue();

        assertEquals(before, quests.getBackend().callsTo("save"), "a discarded engine writes nothing, not even its pending flush");
        assertEquals(0, before, "the change was still pending when the engine was discarded");
        assertNull(QuestEngine.ofIfPresent(quests.getOwner()));
    }

    @Test
    void ofRefusesASectionClassAnotherPluginsEngineKeeps() {
        ECPluginData other = Plugins.fakePluginData("OtherQuests", tempDir.resolve("other").toFile());
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> QuestEngine.of(other, quests.getStorage(), QuestWorld.TestQuestSection.class));
            assertTrue(refused.getMessage().contains("FakeQuests"), refused.getMessage());
        } finally {
            Plugins.forget("OtherQuests");
        }
    }

    @Test
    void ofReturnsTheSameEngineForTheSameArguments() {
        assertSame(engine, QuestEngine.of(quests.getOwner(), quests.getStorage(), QuestWorld.TestQuestSection.class));
        assertSame(engine, QuestEngine.ofIfPresent(quests.getOwner()));
        assertTrue(QuestEngine.all().contains(engine));
    }

    // ------------------------------------------------------------------
    //  Logic stock
    // ------------------------------------------------------------------

    @Test
    void theStartQuestEffectAndTheQuestStatusConditionReachTheEngine() {
        engine.register(quests.parse(MINER));
        List<QuestStartEvent> started = quests.recorded(QuestStartEvent.class);
        Effect startIt = Effects.parse(quests.getOwner(), node("{type: start-quest, quest: miner}"), ZoneOffset.UTC);
        Condition active = Conditions.parse(quests.getOwner(), node("{type: quest-status, quest: miner, is: ACTIVE}"), ZoneOffset.UTC);

        assertFalse(Conditions.test(active, quests.logic().context(alex).owner(alexOwner()).build()).isPassed());
        Effects.apply(quests.logic().context(alex).owner(alexOwner()).build(), startIt);

        assertEquals(1, started.size());
        assertTrue(Conditions.test(active, quests.logic().context(alex).owner(alexOwner()).build()).isPassed());
    }

    private static JsonNode node(String yaml) {
        try {
            return new YAMLMapper().readTree(yaml);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // ------------------------------------------------------------------
    //  A plugin's own source and type
    // ------------------------------------------------------------------

    private SimpleContext captureOf(EventSource source, String species) {
        //the species rides as the native event: the test type reads it back from there
        return new SimpleContext(source, WorldLocPos.of(0, 64, 0, "world"), Actors.player(alex), species);
    }

    static final class CapturePre extends ActionEvent<SimpleContext> implements ECCancellable {
        private boolean cancelled;

        CapturePre(EventSource source, SimpleContext context) {
            super(source, context);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    static final class CapturePost extends ActionEvent<SimpleContext> {
        CapturePost(EventSource source, SimpleContext context) {
            super(source, context);
        }
    }

    static class CaptureType implements ObjectiveType<String> {

        private final EventSource source;
        private final boolean cancels;
        private final String key;
        private final String param;

        CaptureType(EventSource source, boolean cancels) {
            this(source, cancels, "fakequests:capture", "species");
        }

        CaptureType(EventSource source, boolean cancels, String key, String param) {
            this.source = source;
            this.cancels = cancels;
            this.key = key;
            this.param = param;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getDescription() {
            return "Catching a creature.";
        }

        @Override
        public List<ParamSpec> getParams() {
            return Collections.singletonList(ParamSpec.required(param, ParamSpec.Kind.STRING, "The species."));
        }

        @Override
        public ObjectiveNature getNature() {
            return ObjectiveNature.EVENT;
        }

        @Override
        public Set<EventSource> getSources() {
            return new HashSet<>(Collections.singletonList(source));
        }

        @Override
        public String parseFilter(ParsedParams params) {
            return params.getString(param);
        }

        @Override
        public boolean matches(ActionEvent<?> event, String filter) {
            return filter.equals(event.getContext().getNativeEvent());
        }

        @Override
        public boolean cancelsNative() {
            return cancels;
        }

        @Override
        public Class<? extends ActionEvent<?>> getPostEvent(EventSource source) {
            return CapturePost.class;
        }

        @Override
        public Class<? extends ActionEvent<?>> getPreEvent(EventSource source) {
            return CapturePre.class;
        }
    }
}
