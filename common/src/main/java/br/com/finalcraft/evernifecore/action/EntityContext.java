package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An entity acted upon: used ({@link EventSource#INTERACT} - a villager traded with, an item frame turned, a mount
 * ridden), an NPC used ({@link EventSource#INTERACT_NPC}), tamed ({@link EventSource#TAME}), born of breeding
 * ({@link EventSource#BREED}) or sheared ({@link EventSource#SHEAR}). {@link #getActor()} is who acts on it, and
 * {@link #getClickType()} with which click: an NPC is used with either.
 */
public final class EntityContext extends ActionContext {

    /** The click an entity was acted on with: {@link #RIGHT} is the use, {@link #LEFT} the punch. */
    public enum ClickType {
        LEFT,
        RIGHT
    }

    /** The sources an entity context serves. */
    public static final Set<EventSource> SOURCES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(EventSource.INTERACT,
            EventSource.INTERACT_NPC, EventSource.TAME, EventSource.BREED, EventSource.SHEAR)));

    private final Actor entity;
    private final @Nullable TypeKey itemInHandKey;
    private final ClickType clickType;

    /** An entity used with the right click - every source but a punched NPC. */
    public EntityContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent, Actor entity,
                         @Nullable TypeKey itemInHandKey) {
        this(source, position, actor, nativeEvent, entity, itemInHandKey, ClickType.RIGHT);
    }

    /**
     * @param source        one of {@link #SOURCES}
     * @param position      where the entity stands
     * @param entity        the entity used, tamed or sheared; the newborn of a breeding
     * @param itemInHandKey what the actor holds; null for an empty hand, no hand, or a platform that does not say
     * @param clickType     the click it was acted on with
     * @throws IllegalArgumentException for a source outside {@link #SOURCES}
     */
    public EntityContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent, Actor entity,
                         @Nullable TypeKey itemInHandKey, ClickType clickType) {
        super(source, position, actor, nativeEvent);
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("An EntityContext is one of " + SOURCES + ", not " + source
                    + ": pass the source of the entity event being asked about, or build the context that source carries.");
        }
        this.entity = Objects.requireNonNull(entity, "entity");
        this.itemInHandKey = itemInHandKey;
        this.clickType = Objects.requireNonNull(clickType, "clickType");
    }

    /** The entity acted upon: used, tamed, sheared, or the newborn of a breeding. */
    public Actor getEntity() {
        return entity;
    }

    /** What the actor held when it acted - the item handed to an NPC; null for an empty hand or when the platform does not say. */
    public @Nullable TypeKey getItemInHandKey() {
        return itemInHandKey;
    }

    /** The click the entity was acted on with: {@link ClickType#LEFT} only for an NPC punched, {@link ClickType#RIGHT} otherwise. */
    public ClickType getClickType() {
        return clickType;
    }

    @Override
    public void close() {
        super.close();
        entity.close();
    }
}
