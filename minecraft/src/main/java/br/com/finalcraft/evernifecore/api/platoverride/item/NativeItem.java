package br.com.finalcraft.evernifecore.api.platoverride.item;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** A Bukkit {@link ItemStack} carried through platform-agnostic code that only passes it along. */
public final class NativeItem {

    private final ItemStack itemStack;

    public NativeItem(ItemStack itemStack) {
        this.itemStack = Objects.requireNonNull(itemStack, "'itemStack' cannot be null: a NativeItem wraps a real item!");
    }

    public ItemStack getItemStack() {
        return itemStack;
    }
}
