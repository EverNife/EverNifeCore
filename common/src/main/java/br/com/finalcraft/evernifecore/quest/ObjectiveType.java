package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.ActionSupport;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * What an objective counts: breaking a block, catching a creature, holding 32 iron. A plugin adds its own with
 * {@link ObjectiveTypes#register}; the core's stock ones live in {@code quest.core}. An objective node is
 * {@code {id: ore, type: break-block, block: "minecraft:*_ore", goal: 32}}: the keys of {@link ObjectiveTypes#OBJECTIVE_KEYS}
 * are the objective's, every other key is a parameter of this type ({@link #getParams()}).
 *
 * @param <C> the parsed filter, built once per objective (per parameter value for a quest with params)
 */
public interface ObjectiveType<C> {

    /** {@code owner:name}, e.g. {@code evernifecore:break-block} or {@code pokequest:capture}. */
    String getKey();

    /** One sentence for the exported type reference. */
    String getDescription();

    /** The filter's parameters. */
    List<ParamSpec> getParams();

    /** The platforms this type runs on; empty = every platform. */
    default Set<String> getPlatforms() {
        return Collections.emptySet();
    }

    ObjectiveNature getNature();

    /** EVENT: the sources whose action events this type consumes. STATE: empty, the condition's needs drive it. */
    Set<EventSource> getSources();

    /** Builds the filter from the validated parameters; any thread. Throws a {@link TypeRegistryException} that teaches. */
    C parseFilter(ParsedParams params) throws TypeRegistryException;

    /** EVENT: whether {@code event} counts for {@code filter}; on the event's thread, no I/O. */
    default boolean matches(ActionEvent<?> event, C filter) {
        return false;
    }

    /** EVENT: what one match is worth; 1 by default (a stack pickup answers its amount). */
    default long countOf(ActionEvent<?> event, C filter) {
        return 1;
    }

    /**
     * STATE: the condition whose pass means done. Its {@code getNeeds()} are the sources after which it is checked
     * again; no needs means it is sampled by the engine's sweep. The context carries the quest state as the
     * {@code quest} attribute.
     */
    default @Nullable Condition stateOf(C filter) {
        return null;
    }

    /** Where the objective sends the player, for the tracked quest's waypoint; null when it has no place. */
    default @Nullable WorldLocPos targetOf(C filter) {
        return null;
    }

    /**
     * Whether a match that completes the objective cancels the native outcome (the action's {@code Pre}). Such a type
     * counts on the {@code Pre}; a source without one fails at {@link ObjectiveTypes#register}.
     */
    default boolean cancelsNative() {
        return false;
    }

    /**
     * The event posted after {@code source}'s outcome. The catalog's for a core source; a plugin's own source has no
     * catalog entry, so a type over one answers the class its plugin posts.
     */
    default @Nullable Class<? extends ActionEvent<?>> getPostEvent(EventSource source) {
        ActionEvents.Pair pair = ActionEvents.of(source);
        return pair == null ? null : pair.getPost();
    }

    /** The cancellable event posted before {@code source}'s outcome; null where the platform raises none. */
    default @Nullable Class<? extends ActionEvent<?>> getPreEvent(EventSource source) {
        ActionEvents.Pair pair = ActionEvents.of(source);
        return pair == null || !ActionSupport.hasPre(source) ? null : pair.getPre();
    }
}
