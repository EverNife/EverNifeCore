package br.com.finalcraft.evernifecore.minecraft.gui.view;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** The {@link GuiSurface} over a real Bukkit container. */
public final class BukkitGuiSurface implements GuiSurface {

    private final Inventory inventory;

    public BukkitGuiSurface(@Nonnull Inventory inventory) {
        this.inventory = inventory;
    }

    @Nonnull
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public int getSize() {
        return inventory.getSize();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    public void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    @Override
    public void clear(int slot) {
        inventory.setItem(slot, null);
    }

    /** By reference: two containers with the same contents are still two windows. */
    @Override
    public boolean isBackedBy(@Nullable Inventory inventory) {
        return this.inventory == inventory;
    }

}
