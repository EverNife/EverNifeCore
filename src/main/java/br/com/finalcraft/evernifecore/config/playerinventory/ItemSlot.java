package br.com.finalcraft.evernifecore.config.playerinventory;

import br.com.finalcraft.evernifecore.fcitemstack.FCItemStack;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class ItemSlot {

    public final int slot;
    public final FCItemStack fcItemStack;
    public ItemSlot(int slot, FCItemStack fcItemStack) {
        this.slot = slot;
        this.fcItemStack = fcItemStack;
    }

    public int getSlot() {
        return slot;
    }

    public FCItemStack getFcItemStack() {
        return fcItemStack;
    }

    public static List<ItemSlot> fromStackList(List<ItemStack> itemStackList){
        List<ItemSlot> itemSlotList = new ArrayList<>();
        for (int i = 0; i < itemStackList.size(); i++) {
            ItemStack itemStack = itemStackList.get(i);
            if (itemStack != null){
                itemSlotList.add(new ItemSlot(i, new FCItemStack(itemStack)));
            }
        }
        return itemSlotList;
    }

    public static List<ItemSlot> fromStackList(ItemStack[] itemStacks){
        List<ItemSlot> itemSlotList = new ArrayList<>();
        for (int i = 0; i < itemStacks.length; i++) {
            if (itemStacks[i] != null){
                itemSlotList.add(new ItemSlot(i, new FCItemStack(itemStacks[i])));
            }
        }
        return itemSlotList;
    }

    public static ItemStack[] toStackList(List<ItemSlot> itemSlotList){
        OptionalInt maxSize = itemSlotList.stream().mapToInt(ItemSlot::getSlot).max();
        if (!maxSize.isPresent()) return new ItemStack[0];
        ItemStack[] itemStacks = new ItemStack[maxSize.getAsInt() + 1];
        for (ItemSlot itemSlot : itemSlotList) {
            itemStacks[itemSlot.getSlot()] = itemSlot.getFcItemStack().getItemStack();
        }
        return itemStacks;
    }
}
