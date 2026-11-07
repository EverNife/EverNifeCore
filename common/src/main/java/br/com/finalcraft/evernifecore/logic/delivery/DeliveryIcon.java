package br.com.finalcraft.evernifecore.logic.delivery;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * What the deliveries screen draws a {@link Delivery} with: a {@link Theme} whose item the server picks in
 * {@code guis/DeliveriesLayout.yml}, or an item of the plugin's own. Only the item is kept (id, amount, data value): the
 * layout writes the text. A delivery without one draws the layout's entry item. Immutable.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DeliveryIcon {

    /** The usual kinds of delivery, each drawn with the item its {@code THEME_<NAME>} icon has in the deliveries layout. */
    public enum Theme {
        QUEST, MONEY, ITEM, COMMAND, AUCTION, REWARD
    }

    private @Nullable String theme;
    private @Nullable String item;
    private int amount = 1;
    private int data = -1;

    private DeliveryIcon() {
        //Jackson no-arg constructor
    }

    /** Drawn with the item the server gave {@code theme} in the deliveries layout. */
    public static DeliveryIcon of(Theme theme) {
        DeliveryIcon icon = new DeliveryIcon();
        icon.theme = Objects.requireNonNull(theme, "theme").name().toLowerCase(Locale.ROOT);
        return icon;
    }

    /** Drawn as {@code item} (its id, amount and data value), under the layout's text. */
    public static DeliveryIcon of(ItemSpec item) {
        Objects.requireNonNull(item, "item");
        DeliveryIcon icon = new DeliveryIcon();
        icon.item = item.getMaterialId();
        icon.amount = item.getAmount();
        icon.data = item.getData();
        return icon;
    }

    /** The theme; null for an item of its own, or for a theme this version does not know (written by a newer one). */
    public @Nullable Theme getTheme() {
        if (theme == null) {
            return null;
        }
        for (Theme each : Theme.values()) {
            if (each.name().equalsIgnoreCase(theme)) {
                return each;
            }
        }
        return null;
    }

    /** The plugin's own item; null for a theme. */
    public @Nullable ItemSpec getItem() {
        return item == null ? null : ItemSpec.of(item, Math.max(1, amount)).withData(Math.max(-1, data));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DeliveryIcon)) {
            return false;
        }
        DeliveryIcon that = (DeliveryIcon) other;
        return amount == that.amount && data == that.data && Objects.equals(theme, that.theme) && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, item, amount, data);
    }

    @Override
    public String toString() {
        return theme != null ? "theme:" + theme : item + " x" + amount;
    }
}
