package br.com.finalcraft.evernifecore.region.selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A platform-agnostic description of the selection tool: the platform turns it into a native item and maps the
 * material id to whatever name its version uses.
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

    private ItemSpec(String materialId, String displayName, List<String> lore) {
        this.materialId = materialId;
        this.displayName = displayName;
        this.lore = lore;
    }

    /** @param materialId a namespaced id such as {@code minecraft:golden_hoe}; it is lower-cased */
    public static ItemSpec of(String materialId, String displayName, List<String> lore) {
        Objects.requireNonNull(materialId, "materialId");
        return new ItemSpec(materialId.toLowerCase(Locale.ROOT), displayName,
                Collections.unmodifiableList(new ArrayList<>(lore)));
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemSpec)) {
            return false;
        }
        ItemSpec other = (ItemSpec) o;
        return materialId.equals(other.materialId) && Objects.equals(displayName, other.displayName) && lore.equals(other.lore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(materialId, displayName, lore);
    }

    @Override
    public String toString() {
        return "ItemSpec{" + materialId + ", " + displayName + "}";
    }
}
