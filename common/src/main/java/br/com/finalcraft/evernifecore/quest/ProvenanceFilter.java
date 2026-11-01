package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.provenance.OriginKind;
import br.com.finalcraft.evernifecore.action.provenance.Provenance;
import br.com.finalcraft.evernifecore.action.provenance.ProvenanceTracker;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * An objective's {@code provenance: {natural: true, spawner: false, own-drop: false}}: each key written is a test the
 * origin of the action's block, creature or item must answer as written - {@code natural: true} counts only what the
 * world made, {@code spawner: false} leaves spawner mobs out, {@code own-drop: false} leaves out what the player placed
 * or dropped themselves. A key left out tests nothing. The origin comes from the engine owner's
 * {@link ProvenanceTracker}. Immutable.
 */
public final class ProvenanceFilter {

    private final @Nullable Boolean natural;
    private final @Nullable Boolean spawner;
    private final @Nullable Boolean ownDrop;

    private ProvenanceFilter(@Nullable Boolean natural, @Nullable Boolean spawner, @Nullable Boolean ownDrop) {
        this.natural = natural;
        this.spawner = spawner;
        this.ownDrop = ownDrop;
    }

    /** @throws IllegalArgumentException for a key that is not one of the three, or a value that is not true/false */
    public static ProvenanceFilter parse(JsonNode node) {
        if (!node.isObject() || node.size() == 0) {
            throw new IllegalArgumentException("provenance is a map of true/false tests, e.g. {natural: true} or {spawner: false, own-drop: false}.");
        }
        Boolean natural = null;
        Boolean spawner = null;
        Boolean ownDrop = null;
        for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isBoolean()) {
                throw new IllegalArgumentException("provenance." + field.getKey() + " is '" + field.getValue() + "': write true or false.");
            }
            boolean value = field.getValue().asBoolean();
            switch (field.getKey()) {
                case "natural":
                    natural = value;
                    break;
                case "spawner":
                    spawner = value;
                    break;
                case "own-drop":
                    ownDrop = value;
                    break;
                default:
                    throw new IllegalArgumentException("provenance." + field.getKey() + " is not a test - the tests are natural, spawner"
                            + " and own-drop.");
            }
        }
        return new ProvenanceFilter(natural, spawner, ownDrop);
    }

    /** Whether an action on something of {@code origin}, done by {@code player}, passes every written test. */
    public boolean admits(Provenance origin, @Nullable FPlayer player) {
        return (natural == null || natural == (origin.getKind() == OriginKind.NATURAL))
                && (spawner == null || spawner == (origin.getKind() == OriginKind.SPAWNER))
                && (ownDrop == null || ownDrop == origin.isOwn(player));
    }

    /**
     * The origin of what {@code context} acted on: the victim or creature by its uuid, the item entity, else the block
     * at the action's position. NATURAL when the tracker has no record - or no record in memory, for a block.
     */
    static Provenance originOf(ProvenanceTracker tracker, ActionContext context) {
        UUID entity = null;
        if (context instanceof DamageContext) {
            entity = idOf(((DamageContext) context).getVictim());
        } else if (context instanceof EntityContext) {
            entity = idOf(((EntityContext) context).getEntity());
        } else if (context instanceof ItemContext) {
            entity = ((ItemContext) context).getItemEntityId();
        }
        if (entity != null) {
            return tracker.getOrigin(entity);
        }
        if (context instanceof BlockContext && context.getPosition() != null) {
            // ponytail: peek only (no I/O on the action's thread) - a block whose chunk the tracker has not cached reads natural
            Provenance cached = tracker.peekOrigin(WorldBlockPos.from(context.getPosition(), context.getWorld()));
            return cached == null ? Provenance.NATURAL : cached;
        }
        return Provenance.NATURAL;
    }

    private static @Nullable UUID idOf(Actor actor) {
        NativeEntity entity = actor.getNativeEntity();
        return entity == null ? null : entity.getUniqueId();
    }

    @Override
    public String toString() {
        return "provenance{" + (natural == null ? "" : "natural=" + natural + " ") + (spawner == null ? "" : "spawner=" + spawner + " ")
                + (ownDrop == null ? "" : "own-drop=" + ownDrop) + "}";
    }
}
