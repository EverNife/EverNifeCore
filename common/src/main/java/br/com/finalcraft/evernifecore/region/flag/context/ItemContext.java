package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** An item picked up from the ground ({@link EventSource#ITEM_PICKUP}) or thrown on it ({@link EventSource#ITEM_DROP}). */
public final class ItemContext extends ActionContext {

    private final TypeKey itemKey;

    /**
     * @param source   {@link EventSource#ITEM_PICKUP} or {@link EventSource#ITEM_DROP}
     * @param position where the item lies, or where the one dropping it stands
     * @param actor    who picks it up or drops it
     * @param itemKey  what the item is, e.g. {@code minecraft:diamond}
     */
    public ItemContext(EventSource source, WorldLocPos position, Actor actor, @Nullable Object nativeEvent, TypeKey itemKey) {
        super(source, position, actor, nativeEvent);
        if (!source.equals(EventSource.ITEM_PICKUP) && !source.equals(EventSource.ITEM_DROP)) {
            throw new IllegalArgumentException("An ItemContext is an ITEM_PICKUP or an ITEM_DROP, not " + source
                    + ": pass the source of the item event being asked about.");
        }
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
    }

    public TypeKey getItemKey() {
        return itemKey;
    }
}
