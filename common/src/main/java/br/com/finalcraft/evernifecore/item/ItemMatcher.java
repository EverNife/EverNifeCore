package br.com.finalcraft.evernifecore.item;

import java.util.Locale;
import java.util.Objects;

/**
 * Which items count, by material id: what an {@code item} cost takes and an item contribution collects. The id is the
 * agnostic one an {@link ItemSpec} carries ({@code minecraft:diamond}, {@code hytale:Ingredient_Bar_Iron}), compared
 * ignoring case; a bare id ({@code diamond}) matches that name in any namespace. Immutable, any thread.
 */
public final class ItemMatcher {

    private final String materialId;

    private ItemMatcher(String materialId) {
        this.materialId = materialId;
    }

    /** @param materialId {@code namespace:name} or a bare name, kept as written for {@link #toItem} */
    public static ItemMatcher of(String materialId) {
        String id = Objects.requireNonNull(materialId, "materialId").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("An item matcher names a material, e.g. minecraft:diamond - this one is empty.");
        }
        return new ItemMatcher(id);
    }

    public String getMaterialId() {
        return materialId;
    }

    public boolean matches(ItemSpec item) {
        return matchesId(item.getMaterialId());
    }

    /** Whether a native item whose agnostic id is {@code otherMaterialId} counts. */
    public boolean matchesId(String otherMaterialId) {
        String mine = materialId.toLowerCase(Locale.ROOT);
        String other = otherMaterialId.toLowerCase(Locale.ROOT);
        if (mine.indexOf(':') >= 0) {
            return mine.equals(other);
        }
        return other.equals(mine) || other.endsWith(":" + mine);
    }

    /** {@code amount} of the item this matcher names, to hand back what was taken. */
    public ItemSpec toItem(int amount) {
        return ItemSpec.of(materialId, amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemMatcher && ((ItemMatcher) other).materialId.equalsIgnoreCase(materialId);
    }

    @Override
    public int hashCode() {
        return materialId.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return materialId;
    }
}
