package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class FCPlaceholderUtil {

    public static <O extends Object> void parseItemStack(ItemStack itemStack, RegexReplacer<O> regexReplacer, O object){
        if (!itemStack.hasItemMeta()) return;
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta.hasDisplayName()){
            itemMeta.setDisplayName(regexReplacer.apply(itemMeta.getDisplayName(), object));
        }
        if (itemMeta.hasLore()){
            itemMeta.setLore(regexReplacer.apply(itemMeta.getLore(), object));
        }
        itemStack.setItemMeta(itemMeta);
    }

}
