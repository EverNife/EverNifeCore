package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.item.GroundItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What {@link Region#clearItems} took off the ground: each item removed, in the order the platform found them, and the totals. */
public final class ItemClearance {

    private final List<GroundItem> items;

    public ItemClearance(List<GroundItem> items) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** Every item entity removed; an item the filter refused stayed on the ground and is not here. */
    public List<GroundItem> getItems() {
        return items;
    }

    /** How many item entities were removed. */
    public int getEntityCount() {
        return items.size();
    }

    /** How many items they held, every stack summed. */
    public int getUnitCount() {
        int units = 0;
        for (GroundItem item : items) {
            units += item.getAmount();
        }
        return units;
    }

    @Override
    public String toString() {
        return "ItemClearance{entities=" + getEntityCount() + ", units=" + getUnitCount() + "}";
    }
}
