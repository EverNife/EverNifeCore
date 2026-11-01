package br.com.finalcraft.evernifecore.item;

import br.com.finalcraft.evernifecore.api.platoverride.item.NativeItem;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A platform-agnostic description of an item and how many of it: the selection tool, an item a {@code give-item}
 * effect hands out. The platform turns it into a native item and maps the material id to whatever name its version
 * uses - or, when the spec carries a {@link NativeItem}, hands out that item as it is. The descriptive fields stay
 * meaningful either way, for logs and for a platform that cannot read the item. Immutable.
 */
public final class ItemSpec {

    /** The default tool: a golden hoe whose lore teaches the three buttons. */
    public static final ItemSpec DEFAULT_TOOL = of("minecraft:golden_hoe", "§6Region Selector", Arrays.asList(
            "§7Left click: add a point",
            "§7Right click: remove the last point",
            "§7Sneak + right click: confirm"));

    private final String materialId;
    private final String displayName;
    private final List<String> lore;
    private final @Nullable NativeItem nativeItem;
    private final int amount;
    private final int data;

    private ItemSpec(String materialId, String displayName, List<String> lore, @Nullable NativeItem nativeItem, int amount, int data) {
        this.materialId = materialId;
        this.displayName = displayName;
        this.lore = lore;
        this.nativeItem = nativeItem;
        this.amount = amount;
        this.data = data;
    }

    /** @param materialId a namespaced id such as {@code minecraft:golden_hoe}; it is lower-cased */
    public static ItemSpec of(String materialId, String displayName, List<String> lore) {
        Objects.requireNonNull(materialId, "materialId");
        return new ItemSpec(materialId.toLowerCase(Locale.ROOT), displayName,
                Collections.unmodifiableList(new ArrayList<>(lore)), null, 1, -1);
    }

    /**
     * {@code amount} of the plain item {@code materialId}, without a name or lore of its own. The id is kept as written:
     * a Hytale item id is case-sensitive.
     */
    public static ItemSpec of(String materialId, int amount) {
        Objects.requireNonNull(materialId, "materialId");
        return new ItemSpec(materialId.trim(), "", Collections.<String>emptyList(), null, 1, -1).withAmount(amount);
    }

    /** A copy that carries the plugin's own item: the platform hands it out as given, plus the selection mark. */
    public ItemSpec withNativeItem(NativeItem nativeItem) {
        return new ItemSpec(materialId, displayName, lore, Objects.requireNonNull(nativeItem, "nativeItem"), amount, data);
    }

    /** A copy with this name and lore, everything else kept - the id keeps the spelling it was given. */
    public ItemSpec withText(String displayName, List<String> lore) {
        return new ItemSpec(materialId, displayName, Collections.unmodifiableList(new ArrayList<>(lore)), nativeItem, amount, data);
    }

    /**
     * A copy of {@code amount} items.
     *
     * @throws IllegalArgumentException when {@code amount} is not positive
     */
    public ItemSpec withAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("An item amount is at least 1, and " + materialId + " was given " + amount + ".");
        }
        return new ItemSpec(materialId, displayName, lore, nativeItem, amount, data);
    }

    /**
     * A copy with this data value: the colour of a pre-1.13 wool ({@code minecraft:wool} {@code 14} is red), the damage of
     * a tool - what Bukkit's {@code ItemStack.getDurability()} answers. {@code -1} leaves it unspecified.
     *
     * @throws IllegalArgumentException when {@code data} is below {@code -1}
     */
    public ItemSpec withData(int data) {
        if (data < -1) {
            throw new IllegalArgumentException("An item data value is 0 or more, or -1 for none, and " + materialId + " was given " + data + ".");
        }
        return new ItemSpec(materialId, displayName, lore, nativeItem, amount, data);
    }

    /** The data value ({@link #withData}); {@code -1} when unspecified - any data, or a platform that has none. */
    public int getData() {
        return data;
    }

    /** How many of the item; 1 unless {@link #withAmount} said otherwise. */
    public int getAmount() {
        return amount;
    }

    public String getMaterialId() {
        return materialId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    /** The plugin's own item, or null when the platform builds the tool from this description. */
    public @Nullable NativeItem getNativeItem() {
        return nativeItem;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemSpec)) {
            return false;
        }
        ItemSpec other = (ItemSpec) o;
        return materialId.equals(other.materialId) && Objects.equals(displayName, other.displayName) && lore.equals(other.lore)
                && Objects.equals(nativeItem, other.nativeItem) && amount == other.amount && data == other.data;
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialId, displayName, lore, nativeItem, amount, data);
    }

    @Override
    public String toString() {
        return "ItemSpec{" + amount + " x " + materialId + (data < 0 ? "" : ":" + data) + (displayName == null || displayName.isEmpty() ? "" : ", " + displayName) + "}";
    }
}
