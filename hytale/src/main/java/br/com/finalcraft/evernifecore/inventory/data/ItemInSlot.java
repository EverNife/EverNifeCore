package br.com.finalcraft.evernifecore.inventory.data;


import com.hypixel.hytale.server.core.inventory.ItemStack;

public class ItemInSlot {

    protected final int slot;
    protected final ItemStack itemStack;

    public ItemInSlot(int slot, ItemStack itemStack) {
        this.slot = slot;
        this.itemStack = itemStack;
    }

    public int getSlot() {
        return slot;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

}
