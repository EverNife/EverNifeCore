package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * {@code near-npc: {npc: citizens:12, radius: 5}}: passes while the player stands within {@code radius} blocks of the
 * NPC's stored location, in the same world. Read loud: an NPC of a provider nobody registered fails the load.
 */
public final class NearNpcCondition implements Condition {

    private final NpcRef npc;
    private final double radius;

    public NearNpcCondition(NpcRef npc, double radius) {
        this.npc = npc;
        this.radius = radius;
    }

    /** Registers {@code evernifecore:near-npc} under {@code core}. */
    public static void register(ECPluginData core) {
        Conditions.registry().register(core, TypeDescriptor.<Condition>of("evernifecore:near-npc")
                .describe("Passes while the player stands near an NPC, in the same world.")
                .param(ParamSpec.required("npc", ParamSpec.Kind.ID, "The NPC as provider:id, e.g. citizens:12 or znpcsplus:merchant."))
                .param(ParamSpec.required("radius", ParamSpec.Kind.NUMBER, "How many blocks away still counts as near."))
                .factory(params -> new NearNpcCondition(Npcs.requireProvider(NpcRef.parse(params.getString("npc"))),
                        params.getNumber("radius").doubleValue()))
                .build());
    }

    @Override
    public ConditionResult test(ExecutionContext ctx) {
        FPlayer player = ctx.getPlayer();
        FLocation at = player == null ? null : player.getLocation();
        Optional<WorldLocPos> where = Npcs.getLocation(npc);
        String name = Npcs.getName(npc).orElse(npc.toString());
        if (at == null || !where.isPresent()) {
            return ConditionResult.fail(FancyText.of("Go to " + name + "."));
        }
        WorldLocPos position = at.getWorldLocPos();
        if (!position.getWorldName().equals(where.get().getWorldName())) {
            return ConditionResult.fail(FancyText.of("Go to " + name + " (in " + where.get().getWorldName() + ")."));
        }
        double distance = position.distance(where.get());
        return ConditionResult.of(distance <= radius, FancyText.of("Go to " + name + " (" + Math.round(distance) + " blocks away)."));
    }

    /** The answer changes as the player walks. */
    @Override
    public Set<EventSource> getNeeds() {
        return Collections.singleton(EventSource.MOVE);
    }

    @Override
    public String toString() {
        return "near-npc(" + npc + ", " + radius + ")";
    }
}
