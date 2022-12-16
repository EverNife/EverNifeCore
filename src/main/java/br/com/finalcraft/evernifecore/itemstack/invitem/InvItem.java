package br.com.finalcraft.evernifecore.itemstack.invitem;

import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface InvItem {

    public Material getMaterial();

    public String getId();

    public List<ItemInSlot> getItemsFrom(ItemStack itemStack);

    public ItemStack setItemsTo(ItemStack customChest, List<ItemInSlot> itemInSlots);


}
