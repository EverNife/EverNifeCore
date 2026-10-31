package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.hytale.action.HyActionContexts;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import jakarta.annotation.Nonnull;

/**
 * How an {@link ItemSpec} becomes a Hytale item id: a {@code hytale:<id>} names the asset {@code <id>}; an id of
 * another game names nothing here, and the caller says what stands in for it.
 */
public final class HyItemIcons {

    /** What a page cell shows for an item no Hytale asset matches - a plain block, never a tool. */
    public static final String NEUTRAL_ITEM = "Rock_Stone";

    private HyItemIcons() {

    }

    /** The asset id {@code materialId} names, or {@code orElse} when it is not a {@code hytale:} id. */
    @Nonnull
    public static String itemIdOf(@Nonnull String materialId, @Nonnull String orElse) {
        String hytaleId = hytaleIdOrNull(materialId);
        return hytaleId == null ? orElse : hytaleId;
    }

    /**
     * What a cell draws for {@code icon}: the plugin's own native item, else the first {@code hytale:} id among the item
     * and its fallbacks, else {@link #NEUTRAL_ITEM}.
     */
    @Nonnull
    public static String itemIdOf(@Nonnull RenderedIcon icon) {
        ItemSpec item = icon.getItem();
        if (item.getNativeItem() != null && !item.getNativeItem().getItemStack().isEmpty()) {
            return item.getNativeItem().getItemStack().getItemId();
        }
        String id = hytaleIdOrNull(item.getMaterialId());
        if (id != null) {
            return id;
        }
        for (ItemSpec fallback : icon.getFallbacks()) {
            id = hytaleIdOrNull(fallback.getMaterialId());
            if (id != null) {
                return id;
            }
        }
        return NEUTRAL_ITEM;
    }

    private static String hytaleIdOrNull(String materialId) {
        int colon = materialId.indexOf(':');
        return colon > 0 && materialId.substring(0, colon).equals(HyActionContexts.NAMESPACE) ? materialId.substring(colon + 1) : null;
    }

}
