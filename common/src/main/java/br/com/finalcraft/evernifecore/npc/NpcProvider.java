package br.com.finalcraft.evernifecore.npc;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;

import java.util.Optional;

/**
 * One NPC plugin (or platform) the core can ask about its NPCs, registered once with {@link Npcs#register}. An absent
 * plugin is no error: {@link #isAvailable()} answers false and nothing else is asked.
 *
 * <p>A packet NPC (FancyNpcs, ZNPCsPlus) has no entity on the server: {@link #resolve} and {@link #find} keep their empty
 * defaults, and only the click is ever heard of it - never a hit or a death.</p>
 */
public interface NpcProvider {

    /** The {@code provider} half of every {@link NpcRef} this provider answers for, lowercase. */
    String getId();

    /** Whether the plugin is enabled and its API loads. Called often: the answer about the API is looked up once and kept. */
    boolean isAvailable();

    /** Which NPC {@code entity} is; empty for an entity this provider does not know. Thread: that of the event that handed the entity. */
    default Optional<NpcRef> resolve(NativeEntity entity) {
        return Optional.empty();
    }

    /**
     * The NPC's entity now; empty when despawned or when the NPC has no server entity. Thread: main (Bukkit), the world's
     * (Hytale) - from another thread read {@link #getLocation} instead.
     */
    default Optional<NativeEntity> find(NpcRef ref) {
        return Optional.empty();
    }

    /** Where the NPC stands, as its plugin stores it: answered for a despawned NPC too. */
    Optional<WorldLocPos> getLocation(NpcRef ref);

    /** The NPC's display name, colour codes kept. */
    Optional<String> getName(NpcRef ref);

    /**
     * Starts raising {@link EventSource#INTERACT_NPC} from the plugin's own click event. Called by {@link Npcs} only while
     * that source is needed - by a region or a listener of its action event - and {@link #stop()} once it is not.
     */
    default void start() {
    }

    /** Stops what {@link #start()} started. */
    default void stop() {
    }
}
