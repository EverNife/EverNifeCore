package br.com.finalcraft.evernifecore.nms.data.oredict;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface IMCOreRegistry {

    public boolean hasOreName(String oreName);

    public List<String> getAllOreNames();

    public List<String> getOreNamesFrom(ItemStack itemStack);

    public List<ItemStack> getOreItemStacks(String oreName);

    public List<OreDictEntry> getAllOreEntries();

}
