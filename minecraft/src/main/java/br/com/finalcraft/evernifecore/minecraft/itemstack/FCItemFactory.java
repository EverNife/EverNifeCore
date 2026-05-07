package br.com.finalcraft.evernifecore.minecraft.itemstack;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import jakarta.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class FCItemFactory {

    @Nonnull
    public static FCItemBuilder from(@Nonnull List<String> itemDataPart){
        ItemStack itemStack = ItemDataPart.transformItem(itemDataPart);
        return new FCItemBuilder(itemStack);
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull String minecraftOrBukkitIdentifier){//Bukkit OR Minecraft identifier
        ItemStack itemStack = FCItemUtils.fromIdentifier(minecraftOrBukkitIdentifier);
        return new FCItemBuilder(itemStack);
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull final ItemStack itemStack) {
        return new FCItemBuilder(itemStack);
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull final Material material) {
        return new FCItemBuilder(new ItemStack(material));
    }

    @Nonnull
    public static FCItemBuilder itemBuilder() {
        return new FCItemBuilder(new ItemStack(Material.STONE));
    }

}
