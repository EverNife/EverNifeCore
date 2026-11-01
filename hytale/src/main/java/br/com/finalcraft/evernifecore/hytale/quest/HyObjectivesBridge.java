package br.com.finalcraft.evernifecore.hytale.quest;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.ObjectiveFilter;
import br.com.finalcraft.evernifecore.quest.ObjectiveState;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestParams;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.QuestStatus;
import br.com.finalcraft.evernifecore.quest.Stage;
import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.builtin.adventure.objectives.config.ObjectiveAsset;
import com.hypixel.hytale.builtin.adventure.objectives.config.completion.ObjectiveCompletionAsset;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.BlockTagOrItemIdField;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.ObjectiveTaskAsset;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.TaskSet;
import com.hypixel.hytale.builtin.adventure.objectives.config.task.UseBlockObjectiveTaskAsset;
import com.hypixel.hytale.builtin.adventure.objectives.config.taskcondition.TaskConditionAsset;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import jakarta.annotation.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The optional bridge between quests and Hytale's native objectives ({@code Hytale:Objectives}), switched per quest by
 * its {@code hytale-native} map ({@link HyNativeObjectiveSpec}). The native plugin raises no event, so one System scans,
 * once a second per world, on that world's thread:
 * <ul>
 *     <li>{@code markers}: every active objective of a player's quest with a place ({@code targetOf}) holds a native
 *     objective started from an asset made at runtime, whose only task carries the map marker; it is cancelled when our
 *     objective, stage or quest ends.</li>
 *     <li>{@code npc-actions}: a native objective of the quest's {@code asset} started for a player starts the quest;
 *     each native task set it closes - or the whole objective, on completion - completes the quest's objective of the
 *     same position (definition order).</li>
 *     <li>{@code spawns}: when the quest ends without completing, the native objective it follows is cancelled, which
 *     takes back what its spawn beacon brought.</li>
 * </ul>
 * With no registered quest switching any of it on, a scan returns at once. Quests of non-player owners are left out.
 */
public final class HyObjectivesBridge extends TickingSystem<EntityStore> {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final long SCAN_NANOS = 1_000_000_000L;
    private static final String TARGET_ITEM = "EverNifeCore_Quest_Target"; //no such item: the marker task never completes on its own
    private static final HyObjectivesBridge INSTANCE = new HyObjectivesBridge();

    /** One native objective a quest follows. */
    private static final class Mirror {
        final QuestEngine engine;
        final QuestDefinition definition;
        final UUID player;
        final String world;
        int closedTaskSets;

        Mirror(QuestEngine engine, QuestDefinition definition, UUID player, String world) {
            this.engine = engine;
            this.definition = definition;
            this.player = player;
            this.world = world;
        }
    }

    private final Map<String, Long> lastScan = new ConcurrentHashMap<>();
    //"<player>|<quest>|<objective>" -> the native objective that draws its marker
    private final Map<String, UUID> markers = new ConcurrentHashMap<>();
    private final Map<String, String> markerWorld = new ConcurrentHashMap<>();
    //native objective uuid + player -> what it advances
    private final Map<String, Mirror> mirrors = new ConcurrentHashMap<>();
    private final Set<String> loadedAssets = ConcurrentHashMap.newKeySet();

    private HyObjectivesBridge() {
    }

    /**
     * Puts the registration check on every quest engine and the scan System on {@code plugin}'s entity store; true the
     * first time, false after - the server takes a System class once, and only while the plugin sets up.
     */
    public static boolean register(PluginBase plugin) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return false;
        }
        QuestEngine.addDefinitionCheck(HyNativeObjectiveSpec::require);
        plugin.getEntityStoreRegistry().registerSystem(INSTANCE);
        return true;
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        long now = System.nanoTime();
        Long last = lastScan.get(world.getName());
        if (last != null && now - last < SCAN_NANOS) {
            return;
        }
        lastScan.put(world.getName(), now);
        ObjectivePlugin objectives = ObjectivePlugin.get();
        if (objectives == null || (!anyNative() && markers.isEmpty() && mirrors.isEmpty())) {
            return;
        }
        try {
            scanMarkers(objectives, world, store);
            scanMirrors(objectives, world, store);
        } catch (RuntimeException failed) {
            EverNifeCore.getLog().warning("[Quests] The hytale-native scan of world " + world.getName() + " failed: " + failed);
        }
    }

    private static boolean anyNative() {
        for (QuestEngine engine : QuestEngine.all()) {
            for (QuestDefinition definition : engine.getAll()) {
                if (definition.getHytaleNative() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  markers
    // ------------------------------------------------------------------

    private void scanMarkers(ObjectivePlugin objectives, World world, Store<EntityStore> store) {
        Map<String, WorldLocPos> wanted = new HashMap<>();
        for (PlayerRef ref : world.getPlayerRefs()) {
            UUID player = ref.getUuid();
            ProgressOwner owner = ProgressOwner.player(player);
            for (QuestEngine engine : QuestEngine.all()) {
                for (QuestState state : engine.getStates(owner)) {
                    QuestDefinition definition = state.getStatus() == QuestStatus.ACTIVE ? find(engine, state) : null;
                    HyNativeObjectiveSpec spec = definition == null ? null : HyNativeObjectiveSpec.of(definition);
                    Stage stage = spec == null || !spec.isMarkers() || state.getStageId() == null ? null : definition.getStage(state.getStageId());
                    if (stage == null) {
                        continue;
                    }
                    for (Objective objective : stage.getObjectives().getObjectives()) {
                        ObjectiveState progress = state.getObjectives().get(objective.getId());
                        ObjectiveFilter filter = objective.getFilter(QuestParams.of(state.getParams()));
                        WorldLocPos target = filter == null || (progress != null && progress.isDone()) ? null : filter.targetOf();
                        if (target != null && world.getName().equals(target.getWorldName())) {
                            wanted.put(player + "|" + definition.getId() + "|" + objective.getId(), target);
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, UUID> held : markers.entrySet()) {
            if (world.getName().equals(markerWorld.get(held.getKey())) && !wanted.containsKey(held.getKey())) {
                markers.remove(held.getKey());
                markerWorld.remove(held.getKey());
                objectives.cancelObjective(held.getValue(), store);
            }
        }
        for (Map.Entry<String, WorldLocPos> want : wanted.entrySet()) {
            if (markers.containsKey(want.getKey())) {
                continue;
            }
            UUID player = UUID.fromString(want.getKey().substring(0, want.getKey().indexOf('|')));
            var started = objectives.startObjective(loadTargetAsset(want.getValue()), Set.of(player), world.getWorldConfig().getUuid(), null, store);
            if (started != null) {
                markers.put(want.getKey(), started.getObjectiveUUID());
                markerWorld.put(want.getKey(), world.getName());
            }
        }
    }

    /**
     * Makes, once per block, the native objective asset whose only task marks {@code target} on the map, and returns its
     * id. Any thread.
     *
     * @throws IllegalStateException when the asset store refuses an asset made at runtime; quests then need markers
     *                               from an asset of a pack ({@code asset:})
     */
    public static String loadTargetAsset(WorldLocPos target) {
        Vector3i block = new Vector3i((int) Math.floor(target.getX()), (int) Math.floor(target.getY()), (int) Math.floor(target.getZ()));
        String id = "EverNifeCore_QuestTarget_" + block.x + "_" + block.y + "_" + block.z;
        if (INSTANCE.loadedAssets.contains(id) && ObjectiveAsset.getAssetMap().getAsset(id) != null) {
            return id;
        }
        ObjectiveTaskAsset task = new UseBlockObjectiveTaskAsset("evernifecore.quest.target", new TaskConditionAsset[0], new Vector3i[]{block}, 1,
                new BlockTagOrItemIdField(null, TARGET_ITEM));
        ObjectiveAsset asset = new ObjectiveAsset(id, "EverNifeCore", new TaskSet[]{new TaskSet("evernifecore.quest.target", new ObjectiveTaskAsset[]{task})},
                new ObjectiveCompletionAsset[0], "evernifecore.quest.target", "evernifecore.quest.target", false);
        AssetLoadResult<String, ObjectiveAsset> result = ObjectiveAsset.getAssetStore().loadAssets("EverNife:EverNifeCore", List.of(asset));
        if (result.hasFailed() || ObjectiveAsset.getAssetMap().getAsset(id) == null) {
            throw new IllegalStateException("The server refused the objective asset " + id + " made at runtime (failed: " + result.getFailedToLoadKeys()
                    + "); hytale-native markers need an objective asset from a pack instead - name it with asset:.");
        }
        INSTANCE.loadedAssets.add(id);
        return id;
    }

    // ------------------------------------------------------------------
    //  npc-actions and spawns
    // ------------------------------------------------------------------

    private void scanMirrors(ObjectivePlugin objectives, World world, Store<EntityStore> store) {
        Map<String, List<Map.Entry<QuestEngine, QuestDefinition>>> byAsset = new HashMap<>();
        for (QuestEngine engine : QuestEngine.all()) {
            for (QuestDefinition definition : engine.getAll()) {
                HyNativeObjectiveSpec spec = definition.getHytaleNative() == null ? null : HyNativeObjectiveSpec.of(definition);
                if (spec != null && spec.getAsset() != null) {
                    byAsset.computeIfAbsent(spec.getAsset(), any -> new ArrayList<>()).add(Map.entry(engine, definition));
                }
            }
        }
        UUID worldId = world.getWorldConfig().getUuid();
        Set<String> present = new HashSet<>();
        for (var objective : objectives.getObjectiveDataStore().getObjectiveCollection()) {
            List<Map.Entry<QuestEngine, QuestDefinition>> followers = byAsset.get(objective.getObjectiveId());
            if (followers == null || !worldId.equals(objective.getWorldUUID())) {
                continue;
            }
            for (UUID player : objective.getPlayerUUIDs()) {
                for (Map.Entry<QuestEngine, QuestDefinition> follower : followers) {
                    QuestEngine engine = follower.getKey();
                    QuestDefinition definition = follower.getValue();
                    String key = objective.getObjectiveUUID() + "|" + player + "|" + definition.getId();
                    present.add(key);
                    Mirror mirror = mirrors.get(key);
                    ProgressOwner owner = ProgressOwner.player(player);
                    if (mirror == null) {
                        mirror = new Mirror(engine, definition, player, world.getName());
                        mirrors.put(key, mirror);
                        if (engine.getStatus(owner, definition.getId()) != QuestStatus.ACTIVE) {
                            engine.start(owner, definition.getId(), QuestParams.NONE);
                        }
                    }
                    int closed = objective.isCompleted() ? Integer.MAX_VALUE : objective.getCurrentTaskSetIndex();
                    close(mirror, closed);
                    QuestStatus status = engine.getStatus(owner, definition.getId());
                    if (status != QuestStatus.ACTIVE && status != QuestStatus.COMPLETED && !objective.isCompleted()
                            && HyNativeObjectiveSpec.of(definition).isSpawns()) {
                        objectives.cancelObjective(objective.getObjectiveUUID(), store);
                    }
                }
            }
        }
        for (Map.Entry<String, Mirror> held : mirrors.entrySet()) {
            if (world.getName().equals(held.getValue().world) && !present.contains(held.getKey())) {
                //ponytail: a native objective that left the store is read as completed; one cancelled natively completes ours too
                close(held.getValue(), Integer.MAX_VALUE);
                mirrors.remove(held.getKey());
            }
        }
    }

    /** Completes the quest objectives of the native task sets closed since the last scan, up to {@code closedTaskSets}. */
    private static void close(Mirror mirror, int closedTaskSets) {
        List<Objective> all = mirror.definition.getObjectives();
        int upTo = Math.min(closedTaskSets, all.size());
        ProgressOwner owner = ProgressOwner.player(mirror.player);
        for (int position = mirror.closedTaskSets; position < upTo; position++) {
            Objective objective = all.get(position);
            mirror.engine.progress(owner, mirror.definition.getId(), objective.getId(), Math.max(1, objective.getGoal()));
        }
        mirror.closedTaskSets = Math.max(mirror.closedTaskSets, upTo);
    }

    private static @Nullable QuestDefinition find(QuestEngine engine, QuestState state) {
        for (QuestDefinition definition : engine.getAll()) {
            if (definition.getId().toString().equals(state.getQuestId())) {
                return definition;
            }
        }
        return null;
    }
}
