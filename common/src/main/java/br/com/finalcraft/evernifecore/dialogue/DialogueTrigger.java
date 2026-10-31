package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.region.RegionId;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * What starts a registered dialogue by itself: a player using an NPC ({@code npc: citizens:12}) or entering a region
 * ({@code region-enter: myplugin:tavern}). A trigger never interrupts a dialogue the player is already in.
 */
public final class DialogueTrigger {

    private final @Nullable NpcRef npc;
    private final @Nullable RegionId region;

    private DialogueTrigger(@Nullable NpcRef npc, @Nullable RegionId region) {
        this.npc = npc;
        this.region = region;
    }

    /** Starts the dialogue when a player uses {@code npc}. */
    public static DialogueTrigger npc(NpcRef npc) {
        return new DialogueTrigger(Objects.requireNonNull(npc, "npc"), null);
    }

    /** Starts the dialogue when a player enters {@code region}. */
    public static DialogueTrigger regionEnter(RegionId region) {
        return new DialogueTrigger(null, Objects.requireNonNull(region, "region"));
    }

    /** The NPC of an {@code npc:} trigger; null for a region trigger. */
    public @Nullable NpcRef getNpc() {
        return npc;
    }

    /** The region of a {@code region-enter:} trigger; null for an NPC trigger. */
    public @Nullable RegionId getRegion() {
        return region;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DialogueTrigger && Objects.equals(((DialogueTrigger) other).npc, npc)
                && Objects.equals(((DialogueTrigger) other).region, region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(npc, region);
    }

    @Override
    public String toString() {
        return npc != null ? "npc: " + npc : "region-enter: " + region;
    }
}
