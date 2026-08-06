package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemLineException;
import br.com.finalcraft.everylibs.util.FCInputReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

/** Which model of a resource pack the item wears. */
public class ItemDataPartCustomModelData extends ItemDataPart<Integer> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "CustomModelData";
    }

    @Nonnull
    @Override
    public Integer parse(@Nonnull String argument) throws ItemLineException {
        Integer modelData = FCInputReader.parseInt(argument.replace(" ", ""), null);
        if (modelData == null) {
            throw ItemLineException.expecting(argument, "the whole number your resource pack gave the model",
                    "1042");
        }
        return modelData;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull Integer value) {
        return Collections.singletonList(String.valueOf(value));
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull Integer value, @Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(value);
        item.setItemMeta(meta);
        return item;
    }

    /** Asking for it when it is absent throws, so absence has to be checked and not compared to. */
    @Nullable
    @Override
    public Integer extract(@Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
    }

    @Override
    public int getPriority() {
        return PRIORITY_NORMAL;
    }

}
