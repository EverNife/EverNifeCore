package br.com.finalcraft.evernifecore.inventory.invitem;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public interface InvItem {

    public Material getMaterial();

    public String getId();

    public List<ItemInSlot> getItemsFrom(ItemStack itemStack);

    public ItemStack setItemsTo(ItemStack customChest, List<ItemInSlot> itemInSlots);

    public default void onConfigSave(ItemStack itemStack, ConfigSection configSection){
        configSection.setValue("minecraftIdentifier", FCItemUtils.getMinecraftIdentifier(itemStack, false));

        for (ItemInSlot itemInSlot : this.getItemsFrom(itemStack)) {
            configSection.setValue("invItem.content." + itemInSlot.getSlot(), itemInSlot.getItemStack());
        }
    }

    public default ItemStack onConfigLoad(ConfigSection configSection){
        String minecraftIdentifier = configSection.getString("minecraftIdentifier");
        ItemStack customChest = FCItemFactory.from(minecraftIdentifier).build();

        List<ItemInSlot> itemInSlots = new ArrayList<>();
        for (String slot : configSection.getKeys("invItem.content")) {
            ItemStack slotItem = configSection.getLoadable("invItem.content." + slot, ItemStack.class);
            itemInSlots.add(new ItemInSlot(Integer.parseInt(slot), slotItem));
        }

        return this.setItemsTo(customChest, itemInSlots);
    }

}
