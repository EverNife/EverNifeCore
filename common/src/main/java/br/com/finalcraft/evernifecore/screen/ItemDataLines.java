package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The part of the item-data line format ({@code key:argument}) the screen layer reads itself: the item id, the amount,
 * the name and the lore. Every other line ({@code nbt:}, {@code enchant:}, {@code hideflags:}...) is kept raw and
 * materialized by the renderer, which is the only side that knows what its platform can do with it.
 */
public final class ItemDataLines {

    public static final String MATERIAL = "material";
    public static final String AMOUNT = "amount";
    public static final String NAME = "name";
    public static final String LORE = "lore";
    public static final String NBT = "nbt";

    private static final Set<String> MATERIAL_KEYS = keys("material", "type", "id", "identifier");
    private static final Set<String> AMOUNT_KEYS = keys("amount", "number");
    private static final Set<String> NAME_KEYS = keys("name", "text", "title");
    private static final Set<String> LORE_KEYS = keys("lore", "description");

    private ItemDataLines() {

    }

    private static Set<String> keys(String... keys) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(keys)));
    }

    /** The key of a line: what comes before the first {@code :}, trimmed. */
    @Nonnull
    public static String keyOf(@Nonnull String line) {
        int separator = line.indexOf(':');
        return (separator < 0 ? line : line.substring(0, separator)).trim();
    }

    /** What comes after the first {@code :}, as written. */
    @Nonnull
    public static String argumentOf(@Nonnull String line) {
        int separator = line.indexOf(':');
        return separator < 0 ? "" : line.substring(separator + 1);
    }

    private static boolean is(Set<String> keys, String line) {
        return keys.contains(keyOf(line).toLowerCase(Locale.ROOT));
    }

    /** Whether the line carries text - a name or a lore line - which is all a language block may hold. */
    public static boolean isTextLine(@Nullable String line) {
        return line != null && (is(NAME_KEYS, line) || is(LORE_KEYS, line));
    }

    /** Whether the screen layer reads this line itself instead of handing it to the renderer. */
    public static boolean isReadHere(@Nonnull String line) {
        return isTextLine(line) || is(MATERIAL_KEYS, line) || is(AMOUNT_KEYS, line);
    }

    /** The item a {@code DisplayItem} block names; a block without a material line is air. */
    @Nonnull
    public static ItemSpec readItem(@Nonnull List<String> lines) {
        String material = Icon.AIR;
        int amount = 1;
        for (String line : lines) {
            if (is(MATERIAL_KEYS, line)) {
                material = argumentOf(line).trim();
            } else if (is(AMOUNT_KEYS, line)) {
                amount = parseAmount(argumentOf(line).trim());
            }
        }
        return applyText(ItemSpec.of(material, 1).withAmount(amount), lines);
    }

    private static int parseAmount(String argument) {
        try {
            int amount = Integer.parseInt(argument);
            if (amount > 0) {
                return amount;
            }
        } catch (NumberFormatException ignored) {
            //falls through to the refusal below
        }
        throw new IllegalArgumentException("'amount:" + argument + "' is not a stack size. Write a whole number of at "
                + "least 1, as in amount:16.");
    }

    /** The lines this layer does not read, kept in their order for the renderer. */
    @Nonnull
    public static List<String> rawLines(@Nonnull List<String> lines) {
        List<String> raw = new ArrayList<>();
        for (String line : lines) {
            if (!isReadHere(line)) {
                raw.add(line);
            }
        }
        return raw;
    }

    /**
     * {@code item} with the name and lore {@code lines} give: a name line replaces the name, lore lines replace the lore.
     * Both read {@code &} colour codes as {@code §}; a {@code §} already written is kept.
     */
    @Nonnull
    public static ItemSpec applyText(@Nonnull ItemSpec item, @Nonnull List<String> lines) {
        String name = null;
        List<String> lore = null;
        for (String line : lines) {
            if (is(NAME_KEYS, line)) {
                name = FCColorUtil.colorfy(argumentOf(line));
            } else if (is(LORE_KEYS, line)) {
                if (lore == null) {
                    lore = new ArrayList<>();
                }
                lore.add(FCColorUtil.colorfy(argumentOf(line)));
            }
        }
        if (name == null && lore == null) {
            return item;
        }
        return item.withText(name == null ? item.getDisplayName() : name, lore == null ? item.getLore() : lore);
    }

    /**
     * The {@code DisplayItem} lines of an item: material, amount, then text unless it lives elsewhere, then the raw lines.
     * The text is written with {@code &} colour codes, the form an admin reads and edits.
     */
    @Nonnull
    public static List<String> write(@Nonnull ItemSpec item, @Nonnull List<String> dataLines, boolean omitText) {
        List<String> lines = new ArrayList<>();
        lines.add(MATERIAL + ":" + item.getMaterialId());
        if (item.getAmount() != 1) {
            lines.add(AMOUNT + ":" + item.getAmount());
        }
        if (!omitText) {
            if (!item.getDisplayName().isEmpty()) {
                lines.add(NAME + ":" + FCColorUtil.decolorfy(item.getDisplayName()));
            }
            for (String loreLine : item.getLore()) {
                lines.add(LORE + ":" + FCColorUtil.decolorfy(loreLine));
            }
        }
        lines.addAll(dataLines);
        return lines;
    }

}
