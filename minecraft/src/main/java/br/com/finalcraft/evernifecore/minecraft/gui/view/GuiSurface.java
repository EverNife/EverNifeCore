package br.com.finalcraft.evernifecore.minecraft.gui.view;

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

}
