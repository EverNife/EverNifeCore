package br.com.finalcraft.evernifecore.itemstack;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
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
