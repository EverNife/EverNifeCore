package br.com.finalcraft.evernifecore.minecraft.itemstack;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemBase;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ParsedBlock;
import br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder.FCItemBuilder;
import jakarta.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Where a recipe for an item starts. */
public class FCItemFactory {

    /**
     * A recipe from a block of item-data lines, read now.
     *
     * <p>Reading here rather than at build time is what makes a bad line a complaint about the file
     * it is in. Every line that could not be read is reported and costs only itself.</p>
     */
    @Nonnull
    public static FCItemBuilder from(@Nonnull List<String> itemDataPart){
        ParsedBlock block = ItemEngine.get().parse(itemDataPart);
        return new FCItemBuilder(ItemBase.of(Material.STONE), block);
    }

    /**
     * A recipe from a bukkit {@code NAME[:durability]} or a namespaced {@code mod:item}.
     *
     * @throws IllegalArgumentException when a bukkit name is not one this server has
     */
    @Nonnull
    public static FCItemBuilder from(@Nonnull String minecraftOrBukkitIdentifier){
        return new FCItemBuilder(ItemBase.ofIdentifier(minecraftOrBukkitIdentifier));
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull final ItemStack itemStack) {
        return new FCItemBuilder(ItemBase.of(itemStack));
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull final Material material) {
        return new FCItemBuilder(ItemBase.of(material));
    }

}
