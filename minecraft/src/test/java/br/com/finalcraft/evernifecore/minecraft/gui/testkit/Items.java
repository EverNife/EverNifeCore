package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Stacks for a rig with no server behind it.
 *
 * <p>{@link #single(Material)} exists because of a real gap: {@code ItemStack.getMaxStackSize()} asks
 * the material, the material asks the server's item registry, and a headless test has no such registry
 * - so every item answers 64 here, a sword included. An item that does not stack is not a detail in an
 * editable area: it is the whole of the merge arithmetic taking its other branch, and a test that never
 * takes it proves half of what it says. So the stack itself answers what the server would have.</p>
 */
public final class Items {

    private Items() {

    }

    /** An ordinary stack, as big as it says. */
    public static ItemStack of(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    /** One of something that does not stack - a sword, a saddle, a bucket. */
    public static ItemStack single(Material material) {
        return new SingleStack(material);
    }

    /**
     * A stack of one, and it says so itself.
     *
     * <p>Subclassing survives {@code clone()} - {@code ItemStack.clone} goes through
     * {@code Object.clone} - so an item stays non-stackable everywhere the framework and the fake
     * platform carry it. It does NOT survive being written to a file and read back, where an item is
     * rebuilt from its material: after a round trip it is an ordinary stack of one, which is what a
     * server without the registry would have said about it all along.</p>
     */
    private static final class SingleStack extends ItemStack {

        private SingleStack(Material material) {
            super(material, 1);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

    }

}
