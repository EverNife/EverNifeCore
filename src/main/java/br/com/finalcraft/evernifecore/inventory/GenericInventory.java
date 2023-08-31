package br.com.finalcraft.evernifecore.inventory;

import br.com.finalcraft.evernifecore.EverNifeCore;
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

    public GenericInventory(Collection<ItemInSlot> itemsInSlots) {
        for (ItemInSlot itemInSlot : itemsInSlots) {
            this.items.put(itemInSlot.getSlot(), itemInSlot);
        }
    }

    @Override
    public void onConfigSave(ConfigSection section) {
        section.setValue("", null); //Clear content before saving it
        for (ItemInSlot itemInSlot : items.values()) {
            section.setValue(String.valueOf(itemInSlot.getSlot()), itemInSlot.getItemStack());
        }
    }

    @Loadable
    public static GenericInventory onConfigLoad(ConfigSection section){
        List<ItemInSlot> items = new ArrayList<>();

        for (String key : section.getKeys("")) {
            ConfigSection itemSection = section.getConfigSection(key);
            try {
                Integer slot = Integer.parseInt(key);
                ItemStack itemStack = itemSection.getLoadable("", ItemStack.class);
                ItemInSlot itemInSlot = new ItemInSlot(slot,itemStack);
                items.add(itemInSlot);
            }catch (Exception e){
                EverNifeCore.info("Failed to load ItemSlot from [" + itemSection + "]");
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

    public void removeItem(int index){
        items.remove(index);
    }

    public void setItem(int index, ItemStack itemStack){
        if (itemStack == null){
            removeItem(index);
        }else {
            items.put(index, new ItemInSlot(index, itemStack));
        }
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
