package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemLineException;
import br.com.finalcraft.everylibs.util.FCInputReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The enchantments, as {@code namespace:key:level}.
 *
 * <p>The value is the key as text, not a resolved {@link Enchantment}: the registry belongs to a
 * running server and reading the line does not. The level is whatever follows the last {@code :},
 * which is what lets the key keep its own namespace separator.</p>
 */
public class ItemDataPartEnchantment extends ItemDataPart<SortedMap<String, Integer>> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "enchant";
    }

    @Nonnull
    @Override
    public SortedMap<String, Integer> parse(@Nonnull String argument) throws ItemLineException {
        String written = argument.replace(" ", "");
        int separator = written.lastIndexOf(':');
        if (separator <= 0) {
            throw new ItemLineException("'" + argument + "' is missing the level. The value is "
                    + "'<enchantment>:<level>' - for example 'minecraft:sharpness:5'.");
        }

        String key = written.substring(0, separator);
        Integer level = FCInputReader.parseInt(written.substring(separator + 1), null);
        if (level == null) {
            throw new ItemLineException("'" + written.substring(separator + 1) + "' is not a level. "
                    + "The value is '<enchantment>:<level>' - for example 'minecraft:sharpness:5'.");
        }

        SortedMap<String, Integer> enchants = new TreeMap<>();
        enchants.put(key, level);
        return enchants;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull SortedMap<String, Integer> value) {
        List<String> arguments = new ArrayList<>(value.size());
        for (Map.Entry<String, Integer> entry : value.entrySet()) {
            arguments.add(entry.getKey() + ":" + entry.getValue());
        }
        return arguments;
    }

    @Nonnull
    @Override
    public SortedMap<String, Integer> merge(@Nonnull SortedMap<String, Integer> previous,
                                            @Nonnull SortedMap<String, Integer> next) {
        SortedMap<String, Integer> joined = new TreeMap<>(previous);
        joined.putAll(next);
        return joined;
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull SortedMap<String, Integer> value, @Nonnull ItemStack item) {
        for (Map.Entry<String, Integer> entry : value.entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            Enchantment enchantment = key == null ? null : Enchantment.getByKey(key);
            if (enchantment == null) {
                throw new ItemLineException("'" + entry.getKey() + "' is not an enchantment this server "
                        + "has. Write the namespaced key the server registered, such as "
                        + "'minecraft:sharpness'.");
            }
            item.addUnsafeEnchantment(enchantment, entry.getValue());
        }
        return item;
    }

    @Nullable
    @Override
    public SortedMap<String, Integer> extract(@Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) {
            return null;
        }
        SortedMap<String, Integer> enchants = new TreeMap<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            enchants.put(entry.getKey().getKey().toString(), entry.getValue());
        }
        return enchants;
    }

    @Override
    public int getPriority() {
        return PRIORITY_EARLY - 1;
    }

}
