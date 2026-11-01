package br.com.finalcraft.evernifecore.item;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Which items count: what an {@code item} cost takes, an item contribution collects and a shop buys back. The id is the
 * agnostic one an {@link ItemSpec} carries ({@code minecraft:diamond}, {@code hytale:Ingredient_Bar_Iron}), compared
 * ignoring case; a bare id ({@code diamond}) matches that name in any namespace. By default the name and lore of the
 * item are not looked at: {@link #plainOnly()} refuses an item that carries either, and {@link #exact(ItemSpec)} wants
 * the very name and lore of its item. Immutable, any thread.
 */
public final class ItemMatcher {

    private final String materialId;
    private final boolean plainOnly;
    private final @Nullable String displayName; //non-null only for exact(): the name and lore must be these
    private final List<String> lore;

    private ItemMatcher(String materialId, boolean plainOnly, @Nullable String displayName, List<String> lore) {
        this.materialId = materialId;
        this.plainOnly = plainOnly;
        this.displayName = displayName;
        this.lore = lore;
    }

    /** @param materialId {@code namespace:name} or a bare name, kept as written for {@link #toItem} */
    public static ItemMatcher of(String materialId) {
        String id = Objects.requireNonNull(materialId, "materialId").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("An item matcher names a material, e.g. minecraft:diamond - this one is empty.");
        }
        return new ItemMatcher(id, false, null, Collections.<String>emptyList());
    }

    /** The material of {@code item} with exactly its name and lore - a plain item matches only plain items. */
    public static ItemMatcher exact(ItemSpec item) {
        String name = item.getDisplayName() == null ? "" : item.getDisplayName();
        return new ItemMatcher(of(item.getMaterialId()).materialId, false, name, Collections.unmodifiableList(new ArrayList<>(item.getLore())));
    }

    /** This matcher refusing an item that has a name or a lore of its own - a renamed or enchanted-looking one. */
    public ItemMatcher plainOnly() {
        return new ItemMatcher(materialId, true, displayName, lore);
    }

    public String getMaterialId() {
        return materialId;
    }

    public boolean isPlainOnly() {
        return plainOnly;
    }

    public boolean matches(ItemSpec item) {
        if (!matchesId(item.getMaterialId())) {
            return false;
        }
        String name = item.getDisplayName() == null ? "" : item.getDisplayName();
        if (plainOnly && (!name.isEmpty() || !item.getLore().isEmpty())) {
            return false;
        }
        return displayName == null || (displayName.equals(name) && lore.equals(item.getLore()));
    }

    /** Whether a native item whose agnostic id is {@code otherMaterialId} counts, its name and lore unseen. */
    public boolean matchesId(String otherMaterialId) {
        String mine = materialId.toLowerCase(Locale.ROOT);
        String other = otherMaterialId.toLowerCase(Locale.ROOT);
        if (mine.indexOf(':') >= 0) {
            return mine.equals(other);
        }
        return other.equals(mine) || other.endsWith(":" + mine);
    }

    /** {@code amount} of the item this matcher names - with the name and lore of an exact one - to hand back what was taken. */
    public ItemSpec toItem(int amount) {
        ItemSpec item = ItemSpec.of(materialId, amount);
        return displayName == null || (displayName.isEmpty() && lore.isEmpty()) ? item : item.withText(displayName, lore);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ItemMatcher)) {
            return false;
        }
        ItemMatcher that = (ItemMatcher) other;
        return that.materialId.equalsIgnoreCase(materialId) && that.plainOnly == plainOnly && Objects.equals(that.displayName, displayName)
                && that.lore.equals(lore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialId.toLowerCase(Locale.ROOT), plainOnly, displayName, lore);
    }

    @Override
    public String toString() {
        return materialId + (plainOnly ? " (plain)" : "") + (displayName != null && !displayName.isEmpty() ? " named " + displayName : "");
    }
}
