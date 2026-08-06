package br.com.finalcraft.evernifecore.minecraft.gui.view;

import jakarta.annotation.Nullable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Where a gui actually writes. The only thing between the framework and the server's container, and
 * therefore the seam a test replaces to run a whole screen without a server.
 *
 * <p>Every method here runs on the main thread; the commit that calls them refuses to run anywhere
 * else.</p>
 */
public interface GuiSurface {

    int getSize();

    /** What the container currently holds at {@code slot}, or {@code null}. */
    ItemStack getItem(int slot);

    void set(int slot, ItemStack item);

    void clear(int slot);

    /**
     * Whether this surface is the one showing in {@code inventory}. Every event the server sends names
     * the container it happened in, so this is the question that decides whether a click, a drag or a
     * close belongs to a screen at all - and an implementation that cannot answer it is a seam only
     * half the framework goes through.
     *
     * <p>{@code null} is never this surface.</p>
     */
    boolean isBackedBy(@Nullable Inventory inventory);

}
