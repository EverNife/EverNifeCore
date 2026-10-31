package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.waypoint.WaypointTarget;
import jakarta.annotation.Nullable;

/** A waypoint to an NPC: its stored location, read again on every refresh; gone (null) while its provider is not here. */
public final class NpcWaypointTarget implements WaypointTarget {

    private final NpcRef npc;

    /** @throws NpcProviderException when no plugin registered the provider {@code npc} names */
    public NpcWaypointTarget(NpcRef npc) {
        this.npc = Npcs.requireProvider(npc);
    }

    public NpcRef getNpc() {
        return npc;
    }

    @Override
    public @Nullable WorldLocPos locate() {
        return Npcs.getLocation(npc).orElse(null);
    }
}
