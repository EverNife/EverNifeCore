package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestDefinitions;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestIndex;
import br.com.finalcraft.evernifecore.quest.QuestSection;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.RegionOptions;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One plugin that owns quests, for a test: a {@link LogicWorld} (plugin data, in-memory storage that counts calls, the
 * core's stock types, the shared {@link TestClock}, online players) with the PlayerData layer booted, and the plugin's
 * {@link QuestEngine} keeping players in {@link TestQuestSection} and everyone else in the storage. {@link #player}
 * logs a player in with the section loaded; {@link #post} hands an action to whoever listens, as a platform listener
 * does. Needs a platform with {@code mainThreadInline()}.
 *
 * <pre>{@code
 * try (QuestWorld quests = QuestWorld.open("FakeQuests", tempDir)) {
 *     TestFPlayerSender alex = quests.player("Alex");
 *     quests.getEngine().register(quests.parse("{id: miner, stages: [{id: mine, objectives: [{id: ore, type: break-block, goal: 2}]}]}"));
 *     quests.getEngine().start(ProgressOwner.player(alex.getUniqueId()), quests.id("miner"), QuestParams.NONE).join();
 *     quests.post(quests.breakBlock(alex, "minecraft:stone"));
 * }
 * }</pre>
 */
public final class QuestWorld implements AutoCloseable {

    /** The quest section of every QuestWorld; one world open at a time keeps it. */
    public static final class TestQuestSection extends QuestSection {
        public TestQuestSection() {
            //Jackson
        }
    }

    private final LogicWorld logic;
    private final QuestEngine engine;
    private final Map<FPlayer, WorldLocPos> positions = new LinkedHashMap<>();
    private boolean closed;

    private QuestWorld(String ownerName, Path dataFolder) {
        this.logic = LogicWorld.open(ownerName, dataFolder);
        logic.deliveries(); //boots the PlayerData layer the quest section binds to
        this.engine = QuestEngine.of(logic.getOwner(), logic.getStorage(), TestQuestSection.class);
        engine.load().join();
    }

    /** Opens the quests of a fake plugin named {@code ownerName} whose data folder is {@code dataFolder}. */
    public static QuestWorld open(String ownerName, Path dataFolder) {
        return new QuestWorld(ownerName, dataFolder);
    }

    public QuestEngine getEngine() {
        return engine;
    }

    public ECPluginData getOwner() {
        return logic.getOwner();
    }

    public ECStorage getStorage() {
        return logic.getStorage();
    }

    /** The backend under {@link #getStorage()}, for {@code callsTo("save")}. */
    public ScriptedStorage getBackend() {
        return logic.getBackend();
    }

    /** The logic world under this one: contexts, deliveries, audit log, custom variables. */
    public LogicWorld logic() {
        return logic;
    }

    public QuestIndex getIndex() {
        return QuestIndex.get();
    }

    /** {@code name} as a quest id of this world's plugin. */
    public QuestId id(String name) {
        return QuestId.of(getOwner().getMetaInfo().getName(), name);
    }

    /** A definition of this world's plugin read from YAML (JSON is YAML too): {@code {id: x, stages: [...]}}. */
    public QuestDefinition parse(String yaml) {
        return QuestDefinitions.parseYaml(getOwner(), yaml);
    }

    /** Registers the fixture quests of {@link QuestDefinitionsFixture} named {@code names} in this world's engine. */
    public void registerFixtures(String... names) {
        for (String name : names) {
            engine.register(QuestDefinitionsFixture.load(getOwner(), name));
        }
    }

    /**
     * A player online on the platform, logged in with every login section loaded, this world's quest section
     * included, and announced as fully logged in - what makes the engine publish their active quests.
     */
    public TestFPlayerSender player(String name) {
        TestFPlayerSender player = logic.player(name);
        login(player);
        return player;
    }

    /** Logs {@code player} in again: loads the quest section and posts the full login. */
    public void login(FPlayer player) {
        PlayerData data = PlayerController.getOrCreate(player.getUniqueId()).join();
        data.getPDSection(TestQuestSection.class).join();
        ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
    }

    /** Announces {@code player}'s quit to whoever listens; the section stays cached, as during the idle grace. */
    public void quit(FPlayer player) {
        PlayerData data = PlayerController.getLoaded(player.getUniqueId());
        if (data != null) {
            ECEventBus.global().post(new ECPlayerQuitEvent(data));
        }
    }

    public ProgressOwner owner(FPlayer player) {
        return ProgressOwner.player(player.getUniqueId());
    }

    /** {@code player}'s loaded quest section; null when not loaded. */
    public TestQuestSection section(FPlayer player) {
        return PlayerController.getLoadedSection(player.getUniqueId(), TestQuestSection.class);
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    /** What a platform's listener does after the outcome: the source's {@code Post}, built only with a subscriber. */
    public void post(ActionContext context) {
        Actions.post(context.getSource(), () -> context);
    }

    /** What a platform's listener does before the outcome: the source's {@code Pre}; true when somebody cancelled it. */
    public boolean pre(ActionContext context) {
        ActionEvents.Pair pair = ActionEvents.of(context.getSource());
        if (pair == null || !ECEventBus.global().hasListeners(pair.getPre())) {
            return false;
        }
        ActionEvent<?> event = ECEventBus.global().post(pair.newPre(context));
        return ((ECCancellable) event).isCancelled();
    }

    public BlockContext breakBlock(FPlayer player, String blockKey) {
        return new BlockContext(EventSource.BREAK, WorldLocPos.of(0, 64, 0, "world"), Actors.player(player), null, TypeKey.parse(blockKey), null);
    }

    public ItemContext pickup(FPlayer player, String itemKey, int amount) {
        return new ItemContext(EventSource.ITEM_PICKUP, WorldLocPos.of(0, 64, 0, "world"), Actors.player(player), null, TypeKey.parse(itemKey),
                amount, UUID.randomUUID());
    }

    public DamageContext kill(FPlayer killer, String victimKey) {
        return kill(killer, victimKey, WorldLocPos.of(0, 64, 0, "world"));
    }

    public DamageContext kill(FPlayer killer, String victimKey, WorldLocPos at) {
        return new DamageContext(EventSource.KILL, at, Actors.player(killer), null, Actors.entity(victimKey, ECEntityType.MONSTER));
    }

    // ------------------------------------------------------------------
    //  Regions
    // ------------------------------------------------------------------

    /** The region manager of this world's plugin, without storage: its regions are TRANSIENT and leave at {@link #close()}. */
    public RegionManager regions() {
        return RegionManager.of(getOwner());
    }

    /**
     * A box of this world's plugin in {@code world}, with {@code tags}.
     *
     * @throws AssertionError naming the refusal when the manager refused it
     */
    public Region region(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2, String... tags) {
        CreationResult result = regions().create(name, world, CuboidShape.of(BlockPos.of(x1, y1, z1), BlockPos.of(x2, y2, z2)),
                RegionOptions.defaults().withPersistence(Persistence.TRANSIENT).withTags(tags));
        if (!result.isCreated()) {
            throw new AssertionError("The region [" + name + "] was refused: " + result.getRefusal());
        }
        return result.getRegion();
    }

    /**
     * Walks {@code player} to {@code to}, as a platform's move listener does: the first call places them (a join), the
     * next ones move them from where the last left them - posting the region crossings on the way.
     */
    public void move(FPlayer player, WorldLocPos to) {
        WorldLocPos from = positions.put(player, to);
        if (from == null) {
            RegionMovementTracker.get().onJoin(player, to);
        } else {
            RegionMovementTracker.get().onMove(player, from, to, MoveCause.MOVE, null);
        }
    }

    // ------------------------------------------------------------------
    //  Clock and events
    // ------------------------------------------------------------------

    public Instant now() {
        return logic.now();
    }

    /** Moves the shared clock forward; nothing runs until {@link #runDue()}. */
    public void advance(Duration by) {
        logic.advance(by);
    }

    /** Runs every due task on the shared scheduler, once - the engine's sweep and writes among them; how many ran. */
    public int runDue() {
        return logic.runDue();
    }

    /** Every event of {@code type} posted on the global bus since the first call, in order; the list is live. */
    public <T extends IECEvent> List<T> recorded(Class<T> type) {
        return logic.recorded(type);
    }

    /** Closes the engine (writing what it holds), then the logic world: storage, PlayerData layer, plugin, players. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        QuestEngine.closeOwnedBy(getOwner());
        for (FPlayer player : positions.keySet()) {
            RegionMovementTracker.get().onQuit(player);
        }
        RegionManager regions = RegionManager.ofIfPresent(getOwner());
        if (regions != null) {
            CompletableFuture<Void> closing = regions.close(); //its write goes through the shared scheduler
            for (int round = 0; !closing.isDone() && round < 100; round++) {
                runDue();
            }
            closing.join();
        }
        logic.close();
    }

    /** Waits up to 5 s for {@code task}; what a test does with an engine future. */
    public static <T> T await(CompletableFuture<T> task) {
        try {
            return task.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("The quest engine did not answer: " + e, e);
        }
    }
}
