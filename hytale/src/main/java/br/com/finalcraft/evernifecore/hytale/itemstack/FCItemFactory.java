package br.com.finalcraft.evernifecore.hytale.itemstack;

import br.com.finalcraft.evernifecore.hytale.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.hytale.itemstack.itembuilder.FCItemBuilder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nonnull;

import java.util.List;

public class FCItemFactory {

    @Nonnull
    public static FCItemBuilder from(@Nonnull final String itemId) {
        return new FCItemBuilder(new ItemStack(itemId));
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull final ItemStack itemStack) {
        return new FCItemBuilder(itemStack);
    }

    @Nonnull
    public static FCItemBuilder from(@Nonnull List<String> itemDataPart){
        ItemStack itemStack = ItemDataPart.transformItem(itemDataPart);
        return new FCItemBuilder(itemStack);
    }


}
