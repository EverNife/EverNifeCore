package br.com.finalcraft.evernifecore.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import jakarta.annotation.Nonnull;

import java.util.List;

public class FCItemBuilder {

    private ItemStack itemStack;

    public FCItemBuilder(@Nonnull ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack build(){
        return itemStack
                .withQuantity(itemStack.getQuantity() == 1 ? 2 : 1) //Enforce cloning the ItemStack
                .withQuantity(itemStack.getQuantity());
    }

    /**
     * Read the ItemStack to a DataPart String List
     *
     * @return A list of strings.
     */
    @Nonnull
    public List<String> toDataPart(){
        return ItemDataPart.readItem(this.build());
    }

}
