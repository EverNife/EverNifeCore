package br.com.finalcraft.evernifecore.inventory;

import br.com.finalcraft.evernifecore.config.yaml.helper.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.helper.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.data.ItemSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class GenericInventory implements Salvable {

    protected final HashMap<Integer, ItemSlot> items = new HashMap<>();

    public GenericInventory() {
    }

    public GenericInventory(Collection<ItemSlot> itemSlots) {
        for (ItemSlot itemSlot : itemSlots) {
            this.items.put(itemSlot.getSlot(), itemSlot);
        }
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue("items", null); //Clear content before saving it
        for (ItemSlot itemSlot : items.values()) {
            section.setValue("items." + itemSlot.getSlot(), itemSlot.getItemStack());
        }
    }

    @Loadable
    public static GenericInventory onConfigLoad(ConfigSection section){
        List<ItemSlot> items = new ArrayList<>();

        for (String key : section.getKeys("items")) {
            try {
                Integer slot = Integer.parseInt(key);
                ItemStack itemStack = section.getLoadable("items." + slot, ItemStack.class);
                ItemSlot itemSlot = new ItemSlot(slot,itemStack);
                items.add(itemSlot);
            }catch (NumberFormatException e){
                e.printStackTrace();
            }
        }

        return new GenericInventory(items);
    }

    public Collection<ItemSlot> getItems() {
        return items.values();
    }

    public ItemStack getItem(int index){
        ItemSlot itemSlot = items.get(index);
        return itemSlot != null ? itemSlot.getItemStack() : null;
    }

    public void restoreTo(Inventory inventory){

        ItemStack[] inventoryContent = new ItemStack[inventory.getSize()];

        for (int i = 0; i < inventoryContent.length; i++) {
            ItemStack fcItemStack = getItem(i);
            inventoryContent[i] = fcItemStack != null ? fcItemStack : null;
        }

        inventory.setContents(inventoryContent);
    }

}
