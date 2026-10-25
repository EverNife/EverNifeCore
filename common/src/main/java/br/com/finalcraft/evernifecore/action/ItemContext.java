package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An item that changed hands: picked up from the ground ({@link EventSource#ITEM_PICKUP}), thrown on it
 * ({@link EventSource#ITEM_DROP}), crafted ({@link EventSource#CRAFT}), taken out of a furnace ({@link EventSource#SMELT}),
 * fished ({@link EventSource#FISH}), eaten or drunk ({@link EventSource#CONSUME}) or enchanted ({@link EventSource#ENCHANT}).
 */
public final class ItemContext extends ActionContext {

    /** The sources an item context serves. */
    public static final Set<EventSource> SOURCES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(EventSource.ITEM_PICKUP,
            EventSource.ITEM_DROP, EventSource.CRAFT, EventSource.SMELT, EventSource.FISH, EventSource.CONSUME, EventSource.ENCHANT)));

    private final TypeKey itemKey;
    private final int amount;
    private final @Nullable UUID itemEntity;

    /**
     * @param source     one of {@link #SOURCES}
     * @param position   where the item lies, or where the one handling it stands
     * @param actor      who picks it up, drops, crafts, takes, fishes, consumes or enchants it
     * @param itemKey    what the item is, e.g. {@code minecraft:diamond}
     * @param amount     how many: the stack picked up or dropped, the whole amount a craft produced
     * @param itemEntity the item lying in the world, when there is one (a pickup, a drop, a catch); null otherwise
     * @throws IllegalArgumentException for a source outside {@link #SOURCES}
     */
    public ItemContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent, TypeKey itemKey, int amount,
                       @Nullable UUID itemEntity) {
        super(source, position, actor, nativeEvent);
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("An ItemContext is one of " + SOURCES + ", not " + source
                    + ": pass the source of the item event being asked about, or build the context that source carries.");
        }
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
        this.amount = amount;
        this.itemEntity = itemEntity;
    }

    public TypeKey getItemKey() {
        return itemKey;
    }

    /** How many items: the stack picked up or dropped, the whole amount a craft produced (a shift-click included). */
    public int getAmount() {
        return amount;
    }

    /** The item entity lying in the world; null when the action had none, as a craft or a meal. */
    public @Nullable UUID getItemEntityId() {
        return itemEntity;
    }
}
