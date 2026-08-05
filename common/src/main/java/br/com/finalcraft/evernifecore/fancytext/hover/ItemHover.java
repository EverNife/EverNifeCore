package br.com.finalcraft.evernifecore.fancytext.hover;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The item-tooltip hover: renders as {@code HoverEvent.showItem(...)} built from a raw item id/SNBT
 * string - the same payload the legacy {@code "$show_item$"} sentinel used to carry inline inside
 * the hover text itself.
 */
public final class ItemHover implements FancyHover {

    public static final String TYPE_ID = "item";

    /** The sentinel prefix {@code getHoverText()} and the on-disk codec still use for this type's legacy string form. */
    public static final String LEGACY_SENTINEL = "$show_item$";

    private final String rawItem;

    public ItemHover(String rawItem) {
        this.rawItem = rawItem;
    }

    public String rawItem() {
        return rawItem;
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    @Override
    public String toLegacyPayload() {
        return LEGACY_SENTINEL + rawItem;
    }

    @Override
    public String serialize() {
        return rawItem;
    }

    @Override
    public FancyHover deserialize(String payload) {
        return new ItemHover(payload);
    }

    @Override
    public FancyHover replacePayload(UnaryOperator<String> transform) {
        return new ItemHover(this.rawItem); // ItemStack does not trasnform at all
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemHover)) return false;
        return Objects.equals(rawItem, ((ItemHover) o).rawItem);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rawItem);
    }

    @Override
    public String toString() {
        return "ItemHover{rawItem='" + rawItem + "'}";
    }
}
