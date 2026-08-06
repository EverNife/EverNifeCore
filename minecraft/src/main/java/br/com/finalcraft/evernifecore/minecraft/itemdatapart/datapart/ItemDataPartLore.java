package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The text under the name, one line of the file per line of lore.
 *
 * <p>{@code #} is an ordinary character here. It used to break a line in two, which meant a lore
 * reading "Rank #1" became two lines the moment anything re-read and re-wrote the item - and the
 * config codec does exactly that on every load. Only {@code \n} breaks a line now.</p>
 *
 * <p>Many {@code lore:} lines pile up into one block, and that block replaces whatever the item
 * already had. Appending was the old behaviour and it doubled the lore of any icon that was read
 * and written back.</p>
 */
public class ItemDataPartLore extends ItemDataPart<List<String>> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "lore";
    }

    @Nonnull
    @Override
    public List<String> parse(@Nonnull String argument) throws ItemLineException {
        return new ArrayList<>(Arrays.asList(FCColorUtil.colorfy(argument).split("\\R", -1)));
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull List<String> value) {
        List<String> arguments = new ArrayList<>(value.size());
        for (String line : value) {
            arguments.add(FCColorUtil.decolorfy(line));
        }
        return arguments;
    }

    @Nonnull
    @Override
    public List<String> merge(@Nonnull List<String> previous, @Nonnull List<String> next) {
        List<String> joined = new ArrayList<>(previous);
        joined.addAll(next);
        return joined;
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull List<String> value, @Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setLore(value);
        item.setItemMeta(meta);
        return item;
    }

    @Nullable
    @Override
    public List<String> extract(@Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (String line : meta.getLore()) {
            lines.addAll(Arrays.asList(line.split("\\R", -1)));
        }
        return lines;
    }

    @Override
    public int getPriority() {
        return PRIORITY_NORMAL;
    }

}
