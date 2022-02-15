package br.com.finalcraft.evernifecore.itembuilder;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FCItemFactory {

    @NotNull
    public static FCItemBuilder from(@NotNull List<String> itemDataPart){
        ItemStack itemStack = ItemDataPart.transformItem(itemDataPart);
        return new FCItemBuilder(itemStack);
    }

    @NotNull
    public static FCItemBuilder from(@NotNull String minecraftOrBukkitIdentifier){//Bukkit OR Minecraft identifier
        ItemStack itemStack = FCItemUtils.fromIdentifier(minecraftOrBukkitIdentifier);
        return new FCItemBuilder(itemStack);
    }

    @NotNull
    public static FCItemBuilder from(@NotNull final ItemStack itemStack) {
        return new FCItemBuilder(itemStack);
    }

    @NotNull
    public static FCItemBuilder from(@NotNull final Material material) {
        return new FCItemBuilder(new ItemStack(material));
    }

    @NotNull
    public static FCItemBuilder itemBuilder() {
        return new FCItemBuilder(new ItemStack(Material.STONE));
    }

}
