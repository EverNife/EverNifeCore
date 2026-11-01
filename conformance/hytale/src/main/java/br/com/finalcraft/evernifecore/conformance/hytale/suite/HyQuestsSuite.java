package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.QuestCases;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.hytale.quest.HyObjectivesBridge;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import com.hypixel.hytale.builtin.adventure.objectives.Objective;
import com.hypixel.hytale.builtin.adventure.objectives.ObjectivePlugin;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The quest engine on a Hytale server, on the player's world thread: three real {@link BreakBlockEvent}s fired at the
 * synthetic player progress {@code ecc-mine} to 3 ({@link QuestCases}); and whether the server takes an objective asset
 * made at runtime - what {@code hytale-native: {markers: true}} rests on - by loading one and starting it for the
 * player. A refusal is SKIPPED with the server's reason: markers then need an asset from a pack.
 */
public final class HyQuestsSuite implements ConformanceSuite {

    private final ECPluginData plugin;

    public HyQuestsSuite(ECPluginData plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "quests";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                HyRegionsSuite.withPlayer("break-block-progresses", ctx, player -> {
                    PlayerRef ref = HyWorld.player(player);
                    World world = HyWorld.worldOf(ref);
                    return QuestCases.breakBlockProgresses(plugin, player, () -> HyWorld.on(world, () -> {
                        Vector3d at = HyWorld.position(ref);
                        for (int i = 0; i < QuestCases.MINE_GOAL; i++) {
                            HyRegionsSuite.firedCancelled(ref.getReference(), HyRegionsSuite.breakAt(world, HyRegionsSuite.block(at, 2 + i, -1, 0)));
                        }
                        return null;
                    }));
                }),
                HyRegionsSuite.withPlayer("native-marker-accepts-runtime-asset", ctx, player -> {
                    PlayerRef ref = HyWorld.player(player);
                    World world = HyWorld.worldOf(ref);
                    return HyWorld.on(world, () -> {
                        ObjectivePlugin objectives = ObjectivePlugin.get();
                        if (objectives == null) {
                            return Verdict.skipped("the Hytale:Objectives plugin is not enabled here");
                        }
                        Vector3d at = HyWorld.position(ref);
                        String asset;
                        try {
                            asset = HyObjectivesBridge.loadTargetAsset(WorldLocPos.of(at.x + 8, at.y, at.z, world.getName()));
                        } catch (IllegalStateException refused) {
                            return Verdict.skipped(refused.getMessage());
                        }
                        Objective started = objectives.startObjective(asset, Collections.singleton(ref.getUuid()),
                                world.getWorldConfig().getUuid(), null, ref.getReference().getStore());
                        if (started == null) {
                            return Verdict.skipped("the asset " + asset + " loaded, but startObjective answered null for the player");
                        }
                        objectives.cancelObjective(started.getObjectiveUUID(), ref.getReference().getStore());
                        return Verdict.pass("loaded " + asset + ", started " + started.getObjectiveUUID() + " for " + player.getName() + ", cancelled");
                    });
                }));
    }
}
