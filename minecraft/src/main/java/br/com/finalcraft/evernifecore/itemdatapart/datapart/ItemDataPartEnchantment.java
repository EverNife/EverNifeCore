package br.com.finalcraft.evernifecore.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ItemDataPartEnchantment extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        int idx = argument.lastIndexOf(':');

        String enchantKey = idx != -1 ? argument.substring(0, idx) : argument;
        String amountString = idx != -1 ? argument.substring(idx + 1) : null;

        if (enchantKey == null || amountString == null) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'.");
            return item;
        }

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.fromString(enchantKey));

        if (enchantment == null) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'.");
            return item;
        }

        Integer level = FCInputReader.parseInt(amountString);

        if (level == null) {
            EverNifeCore.getLog().warning("Mistake in Config: '" + argument + "' is not a valid '" + used_name + "'.");
            return item;
        }

        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        ItemMeta baseMeta = base_item.getItemMeta();
        ItemMeta oterMeta = other_item.getItemMeta();

        if (baseMeta.hasEnchants()) {
            if (!oterMeta.hasEnchants()) {
                return false;
            }

            Map<Enchantment, Integer> baseEnchants = baseMeta.getEnchants();
            Map<Enchantment, Integer> otherEnchants = oterMeta.getEnchants();

            if (baseEnchants.size() != otherEnchants.size()) {
                return false;
            }

            for (Map.Entry<Enchantment, Integer> entry : baseEnchants.entrySet()) {
                Enchantment enchantment = entry.getKey();
                Integer level = entry.getValue();

                Integer otherLevel = otherEnchants.get(enchantment);
                if (!level.equals(otherLevel)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public List<String> read(ItemStack itemStack, List<String> output) {

        List<String> enchants = itemStack.getItemMeta().getEnchants().entrySet().stream()
                .map(entry -> entry.getKey().getKey() + ":" + entry.getValue())
                .collect(Collectors.toList());

        Collections.sort(enchants);

        for (String enchant : enchants) {
            output.add("enchant:" + enchant);
        }

        return output;
    }

    @Override
    public int getPriority() {
        return PRIORITY_EARLY - 1;
    }

    @Override
    public boolean removeSpaces() {
        return true;
    }

    @Override
    public String[] createNames() {
        return new String[]{"enchant"};
    }


}
