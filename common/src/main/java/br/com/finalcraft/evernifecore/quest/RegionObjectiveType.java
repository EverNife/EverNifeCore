package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * An objective counted on region borders: it reads the {@link RegionCrossedEvent}s of the player instead of an action
 * event. While one is active the index keeps {@link EventSource#MOVE} needed - what makes the platform follow players
 * across borders - and offers it every crossing of the owners it counts for.
 *
 * @param <C> the parsed filter
 */
public interface RegionObjectiveType<C> extends ObjectiveType<C> {

    @Override
    default ObjectiveNature getNature() {
        return ObjectiveNature.EVENT;
    }

    @Override
    default Set<EventSource> getSources() {
        return Collections.singleton(EventSource.MOVE);
    }

    /** No action event: crossings come from the region system. */
    @Override
    default @Nullable Class<? extends ActionEvent<?>> getPostEvent(EventSource source) {
        return null;
    }

    @Override
    default @Nullable Class<? extends ActionEvent<?>> getPreEvent(EventSource source) {
        return null;
    }

    /** What {@code event} is worth for {@code filter}; 0 when it does not count. On the crossing's thread, no I/O. */
    long countOf(RegionCrossedEvent event, C filter);

    /**
     * A timed type ({@code stay-in-region}) measures time instead of counting crossings: the engine calls
     * {@link #accrue} on every crossing of the player and on its sweep.
     */
    default boolean isTimed() {
        return false;
    }

    /**
     * Timed: brings {@code state} up to {@code now} from where {@code player} stands, under the owner's lock; true when
     * the objective is done.
     */
    default boolean accrue(UUID player, C filter, ObjectiveState state, Instant now) {
        return false;
    }

    /** The definition holding the objective was registered in {@code engine}: a type that needs a region creates it here. */
    default void onRegister(QuestEngine engine, QuestId quest, Objective objective, C filter) {
    }

    /** The definition left {@code engine} (unregistered, replaced or closed): undo what {@link #onRegister} made. */
    default void onUnregister(QuestEngine engine, QuestId quest, Objective objective, C filter) {
    }
}
