package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

/** The name shown on the item. Written with {@code &} codes, held with the section character. */
public class ItemDataPartName extends ItemDataPart<String> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "name";
    }

    @Nonnull
    @Override
    public String parse(@Nonnull String argument) throws ItemLineException {
        return FCColorUtil.colorfy(argument);
    }

    /** A name centred with spaces is a name, and it has to survive being saved and read again. */
    @Override
    public boolean trimsArgument() {
        return false;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull String value) {
        return Collections.singletonList(FCColorUtil.decolorfy(value));
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull String value, @Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(value);
        item.setItemMeta(meta);
        return item;
    }

    @Nullable
    @Override
    public String extract(@Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    @Override
    public int getPriority() {
        return PRIORITY_NORMAL;
    }

}
