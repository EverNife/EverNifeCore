package br.com.finalcraft.evernifecore.inventory;

import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Salvable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class GenericInventory implements Salvable {

    protected final HashMap<Integer, ItemInSlot> items = new HashMap<>();

    public GenericInventory() {
    }

    public GenericInventory(Collection<ItemInSlot> itemInSlots) {
        for (ItemInSlot itemInSlot : itemInSlots) {
            this.items.put(itemInSlot.getSlot(), itemInSlot);
        }
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue("items", null); //Clear content before saving it
        for (ItemInSlot itemInSlot : items.values()) {
            section.setValue("items." + itemInSlot.getSlot(), itemInSlot.getItemStack());
        }
    }

    @Loadable
    public static GenericInventory onConfigLoad(ConfigSection section){
        List<ItemInSlot> items = new ArrayList<>();

        for (String key : section.getKeys("items")) {
            try {
                Integer slot = Integer.parseInt(key);
                ItemStack itemStack = section.getLoadable("items." + slot, ItemStack.class);
                ItemInSlot itemInSlot = new ItemInSlot(slot,itemStack);
                items.add(itemInSlot);
            }catch (NumberFormatException e){
                e.printStackTrace();
            }
        }

        return new GenericInventory(items);
    }

    public Collection<ItemInSlot> getItems() {
        return items.values();
    }

    public ItemStack getItem(int index){
        ItemInSlot itemInSlot = items.get(index);
        return itemInSlot != null ? itemInSlot.getItemStack() : null;
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
