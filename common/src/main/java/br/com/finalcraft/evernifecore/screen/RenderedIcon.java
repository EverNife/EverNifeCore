package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What one viewer sees on one slot, already resolved: the item with its name, lore and amount in that viewer's language
 * and with every placeholder answered, the items to fall back on when the renderer does not know the first one, and the
 * item-data lines the screen layer does not read itself ({@code nbt:}, {@code enchant:}...) - those the renderer
 * materializes. A value: two renders that look the same are {@code equals}, which is the whole diff.
 */
public final class RenderedIcon {

    private final ItemSpec item;
    private final List<ItemSpec> fallbacks;
    private final List<String> dataLines;

    public RenderedIcon(@Nonnull ItemSpec item, @Nonnull List<ItemSpec> fallbacks, @Nonnull List<String> dataLines) {
        this.item = Objects.requireNonNull(item, "item");
        this.fallbacks = Collections.unmodifiableList(new ArrayList<>(fallbacks));
        this.dataLines = Collections.unmodifiableList(new ArrayList<>(dataLines));
    }

    /** The item to draw, text resolved. */
    @Nonnull
    public ItemSpec getItem() {
        return item;
    }

    /** What to draw instead, in order, when the renderer has no item under {@link #getItem()}'s id. */
    @Nonnull
    public List<ItemSpec> getFallbacks() {
        return fallbacks;
    }

    /** Item-data lines for the renderer to apply as written, never interpreted here. */
    @Nonnull
    public List<String> getDataLines() {
        return dataLines;
    }

    /** Air: nothing to draw. A slot whose topmost paint is empty shows what is under it. */
    public boolean isEmpty() {
        return Icon.AIR.equals(item.getMaterialId()) && item.getNativeItem() == null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RenderedIcon)) {
            return false;
        }
        RenderedIcon other = (RenderedIcon) o;
        return item.equals(other.item) && fallbacks.equals(other.fallbacks) && dataLines.equals(other.dataLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, fallbacks, dataLines);
    }

    @Override
    public String toString() {
        return "RenderedIcon{" + item + (dataLines.isEmpty() ? "" : ", " + dataLines) + "}";
    }

}
