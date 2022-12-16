package br.com.finalcraft.evernifecore.inventory.data;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

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

    public static List<ItemInSlot> fromStackList(List<ItemStack> itemStackList){
        List<ItemInSlot> itemInSlotList = new ArrayList<>();
        for (int i = 0; i < itemStackList.size(); i++) {
            ItemStack itemStack = itemStackList.get(i);
            if (itemStack != null){
                itemInSlotList.add(new ItemInSlot(i, new ItemStack(itemStack)));
            }
        }
        return itemInSlotList;
    }

    public static List<ItemInSlot> fromStackList(ItemStack[] itemStacks){
        List<ItemInSlot> itemInSlotList = new ArrayList<>();
        for (int i = 0; i < itemStacks.length; i++) {
            if (itemStacks[i] != null){
                itemInSlotList.add(new ItemInSlot(i, new ItemStack(itemStacks[i])));
            }
        }
        return itemInSlotList;
    }

    public static ItemStack[] toStackList(List<ItemInSlot> itemInSlotList){
        OptionalInt maxSize = itemInSlotList.stream().mapToInt(ItemInSlot::getSlot).max();
        if (!maxSize.isPresent()) return new ItemStack[0];
        ItemStack[] itemStacks = new ItemStack[maxSize.getAsInt() + 1];
        for (ItemInSlot itemInSlot : itemInSlotList) {
            itemStacks[itemInSlot.getSlot()] = itemInSlot.getItemStack();
        }
        return itemStacks;
    }
}
