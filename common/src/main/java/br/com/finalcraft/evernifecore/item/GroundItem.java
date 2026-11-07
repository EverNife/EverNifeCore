package br.com.finalcraft.evernifecore.item;

import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import jakarta.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * An item entity lying on the ground, read once: what it is, how many, the item itself (carrying the platform's stack,
 * {@link ItemSpec#getNativeItem()}) and where it lay. Immutable - removing the entity does not change it.
 */
public final class GroundItem {

    private final TypeKey itemKey;
    private final int amount;
    private final ItemSpec item;
    private final WorldLocPos position;
    private final @Nullable UUID entityId;

    /**
     * @param itemKey  what the item is, e.g. {@code minecraft:diamond}
     * @param amount   how many the stack holds
     * @param item     the item itself
     * @param position where the entity lay
     * @param entityId the entity's id; null when the platform gives it none
     */
    public GroundItem(TypeKey itemKey, int amount, ItemSpec item, WorldLocPos position, @Nullable UUID entityId) {
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
        this.amount = amount;
        this.item = Objects.requireNonNull(item, "item");
        this.position = Objects.requireNonNull(position, "position");
        this.entityId = entityId;
    }

    public TypeKey getItemKey() {
        return itemKey;
    }

    public int getAmount() {
        return amount;
    }

    public ItemSpec getItem() {
        return item;
    }

    public WorldLocPos getPosition() {
        return position;
    }

    public @Nullable UUID getEntityId() {
        return entityId;
    }

    @Override
    public String toString() {
        return amount + "x " + itemKey + " at " + position;
    }
}
