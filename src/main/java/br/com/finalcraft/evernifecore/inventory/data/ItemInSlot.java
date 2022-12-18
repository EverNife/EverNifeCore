package br.com.finalcraft.evernifecore.inventory.data;

import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

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

    public static List<ItemInSlot> fromStacks(Collection<ItemStack> itemStacks){
        List<ItemInSlot> itemsInSlotList = new ArrayList<>();
        int count = 0;
        for (ItemStack itemStack : itemStacks) {
            if (itemStack != null){
                itemsInSlotList.add(new ItemInSlot(count, FCItemFactory.from(itemStack.clone()).build()));//Store a VALIDATED copy
            }
            count++;
        }
        return itemsInSlotList;
    }

    public static List<ItemInSlot> fromStacks(ItemStack... itemStacks){
        return fromStacks(Arrays.asList(itemStacks));
    }

    public static ItemStack[] toArray(Collection<ItemInSlot> itemInSlots){
        OptionalInt maxSize = itemInSlots.stream().mapToInt(ItemInSlot::getSlot).max();
        if (!maxSize.isPresent()) return new ItemStack[0];
        ItemStack[] itemStacks = new ItemStack[maxSize.getAsInt() + 1];
        for (ItemInSlot itemInSlot : itemInSlots) {
            itemStacks[itemInSlot.getSlot()] = itemInSlot.getItemStack();//Store a VALIDATED copy
        }
        return itemStacks;
    }
}
