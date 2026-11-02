package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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
    private @Nullable Supplier<ItemSpec> itemSource;
    private @Nullable ItemSpec item;

    /**
     * @param source     one of {@link #SOURCES}
     * @param position   where the item lies, or where the one handling it stands
     * @param actor      who picks it up, drops, crafts, takes, fishes, consumes or enchants it
     * @param itemKey    what the item is, e.g. {@code minecraft:diamond}
     * @param amount     how many: the stack picked up or dropped, the whole amount a craft produced
     * @param itemEntity the item lying in the world, when there is one (a pickup, a drop, a catch); null otherwise
     * @param item       builds the item itself, asked once on the first {@link #getItem()}; null when the event carries no stack
     * @throws IllegalArgumentException for a source outside {@link #SOURCES}
     */
    public ItemContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent, TypeKey itemKey, int amount,
                       @Nullable UUID itemEntity, @Nullable Supplier<ItemSpec> item) {
        super(source, position, actor, nativeEvent);
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("An ItemContext is one of " + SOURCES + ", not " + source
                    + ": pass the source of the item event being asked about, or build the context that source carries.");
        }
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
        this.amount = amount;
        this.itemEntity = itemEntity;
        this.itemSource = item;
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

    /**
     * The item itself, carrying the platform's stack ({@link ItemSpec#getNativeItem()}) with its name and lore. Built on
     * the first call, so a listener that never asks pays no copy - but a stack the event changed before that call is read
     * as changed: ask while the event is still running. Null when the event carries no stack: a smelt on Bukkit (the
     * furnace event names only the material), a craft on Hytale (the recipe names only its output), a context built by
     * hand without one.
     */
    public synchronized @Nullable ItemSpec getItem() {
        if (itemSource != null) {
            item = itemSource.get();
            itemSource = null;
        }
        return item;
    }
}
