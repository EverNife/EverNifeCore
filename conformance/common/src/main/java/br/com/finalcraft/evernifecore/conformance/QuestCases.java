package br.com.finalcraft.evernifecore.conformance;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestDefinitions;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestIndex;
import br.com.finalcraft.evernifecore.quest.QuestParams;
import br.com.finalcraft.evernifecore.quest.QuestStatus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The quest engine on a live server, both platforms: an in-memory engine of the case's plugin with three small quests -
 * {@value #MINE} (break 3 blocks), {@value #PICKUP} (pick up 5 items) and {@value #COMMAND} (ten kills, moved here only by the
 * {@code progress} verb moves). Each case resets its quest for the player before it starts it, so a run repeats.
 */
public final class QuestCases {

    public static final String MINE = "ecc-mine";
    public static final String PICKUP = "ecc-pickup";
    public static final String COMMAND = "ecc-cmd";
    public static final int MINE_GOAL = 3;
    public static final int PICKUP_GOAL = 5;

    private QuestCases() {
    }

    /** The plugin's in-memory engine with the three quests registered once. Any thread. */
    public static synchronized QuestEngine engine(ECPluginData plugin) {
        QuestEngine engine = QuestEngine.of(plugin);
        if (engine.getAll().isEmpty()) {
            engine.register(QuestDefinitions.parseYaml(plugin, "{id: " + MINE + ", stages: [{id: dig, objectives:"
                    + " [{id: ore, type: break-block, goal: " + MINE_GOAL + "}]}]}"));
            engine.register(QuestDefinitions.parseYaml(plugin, "{id: " + PICKUP + ", stages: [{id: take, objectives:"
                    + " [{id: loot, type: pickup, goal: " + PICKUP_GOAL + "}]}]}"));
            engine.register(QuestDefinitions.parseYaml(plugin, "{id: " + COMMAND + ", stages: [{id: count, objectives:"
                    + " [{id: tally, type: kill, goal: 10}]}]}"));
        }
        return engine;
    }

    public static QuestDefinition quest(QuestEngine engine, String name) {
        for (QuestDefinition definition : engine.getAll()) {
            if (definition.getId().getName().equals(name)) {
                return definition;
            }
        }
        throw new IllegalStateException("The conformance engine has no quest " + name + ": " + engine.getAll());
    }

    /** Resets then starts {@code name} for {@code player}; the future answers once it is active. */
    public static CompletableFuture<QuestStatus> restart(QuestEngine engine, FPlayer player, String name) {
        QuestDefinition definition = quest(engine, name);
        ProgressOwner owner = ProgressOwner.player(player.getUniqueId());
        return engine.reset(owner, definition.getId())
                .thenCompose(reset -> engine.start(owner, definition.getId(), QuestParams.NONE))
                .thenApply(started -> engine.getStatus(owner, definition.getId()));
    }

    /**
     * {@value #MINE}: BREAK is listened only while it is active; {@code breakThree} raises three real block breaks by
     * the player (the platform's own event, on its own thread) and the quest counts exactly 3 and completes.
     */
    public static CompletableFuture<Verdict> breakBlockProgresses(ECPluginData plugin, FPlayer player, Supplier<CompletableFuture<?>> breakThree) {
        QuestEngine engine = engine(plugin);
        QuestDefinition mine = quest(engine, MINE);
        ProgressOwner owner = ProgressOwner.player(player.getUniqueId());
        boolean before = listened();
        return restart(engine, player, MINE).thenCompose(started -> {
            boolean during = listened();
            return breakThree.get().thenApply(done -> {
                long count = engine.getState(owner, mine.getId()).map(state -> state.getCount("ore")).orElse(-1L);
                QuestStatus status = engine.getStatus(owner, mine.getId());
                boolean after = listened();
                return Verdict.of(started == QuestStatus.ACTIVE && during && !after && count == MINE_GOAL && status == QuestStatus.COMPLETED,
                        "listened before=" + before + " during=" + during + " after=" + after + " started=" + started
                                + " count=" + count + " status=" + status);
            });
        });
    }

    /** {@value #PICKUP}: after {@code dropStack} hands the player one stack of {@value #PICKUP_GOAL}, the quest counted its amount. */
    public static CompletableFuture<Verdict> pickupCountsStack(ECPluginData plugin, FPlayer player, Supplier<CompletableFuture<?>> dropStack) {
        QuestEngine engine = engine(plugin);
        QuestDefinition pickup = quest(engine, PICKUP);
        ProgressOwner owner = ProgressOwner.player(player.getUniqueId());
        return restart(engine, player, PICKUP).thenCompose(started -> dropStack.get().thenApply(done -> {
            long count = engine.getState(owner, pickup.getId()).map(state -> state.getCount("loot")).orElse(-1L);
            QuestStatus status = engine.getStatus(owner, pickup.getId());
            return Verdict.of(count == PICKUP_GOAL && status == QuestStatus.COMPLETED, "started=" + started + " count=" + count + " status=" + status);
        }));
    }

    /**
     * {@value #COMMAND} through the plugin's mounted quest verbs: {@code dispatch} runs a console command line and
     * answers whether the server took it; {@code nextTick} reads after the verb's future settled.
     */
    public static CompletableFuture<Verdict> commandsRoundTrip(ECPluginData plugin, FPlayer player, String mount,
                                                             Function<String, Boolean> dispatch, Function<Supplier<Verdict>, CompletableFuture<Verdict>> nextTick) {
        QuestEngine engine = engine(plugin);
        QuestDefinition counter = quest(engine, COMMAND);
        ProgressOwner owner = ProgressOwner.player(player.getUniqueId());
        String id = counter.getId().toString();
        return engine.reset(owner, counter.getId()).thenCompose(reset -> {
            boolean started = dispatch.apply(mount + " start " + id + " " + player.getName());
            return nextTick.apply(() -> {
                QuestStatus afterStart = engine.getStatus(owner, counter.getId());
                boolean progressed = dispatch.apply(mount + " progress " + id + " tally 2 " + player.getName());
                boolean info = dispatch.apply(mount + " info " + id);
                long count = engine.getState(owner, counter.getId()).map(state -> state.getCount("tally")).orElse(-1L);
                return Verdict.of(started && progressed && info && afterStart == QuestStatus.ACTIVE && count == 2,
                        "dispatched start=" + started + " progress=" + progressed + " info=" + info + " status=" + afterStart + " tally=" + count);
            });
        });
    }

    private static boolean listened() {
        return QuestIndex.get().getListenedSources().contains(EventSource.BREAK);
    }
}
