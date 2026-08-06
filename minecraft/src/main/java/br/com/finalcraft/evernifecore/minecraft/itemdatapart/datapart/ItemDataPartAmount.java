package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import br.com.finalcraft.everylibs.util.FCInputReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * How many.
 *
 * <p>Emitted even when it is one. The canonical file is an editing surface, not a minimal dump: the
 * amount is a field admins change constantly, and a line that is already there costs them one
 * keystroke while an absent one costs them the whole line.</p>
 */
public class ItemDataPartAmount extends ItemDataPart<Integer> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "amount";
    }

    @Nonnull
    @Override
    public Integer parse(@Nonnull String argument) throws ItemLineException {
        Integer amount = FCInputReader.parseInt(argument.replace(" ", ""), null);
        if (amount == null) {
            throw ItemLineException.expecting(argument, "a whole number of items", "16");
        }
        return amount;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull Integer value) {
        return Collections.singletonList(String.valueOf(value));
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull Integer value, @Nonnull ItemStack item) {
        item.setAmount(value);
        return item;
    }

    @Nullable
    @Override
    public Integer extract(@Nonnull ItemStack item) {
        return item.getAmount();
    }

    @Override
    public int getPriority() {
        return PRIORITY_EARLY;
    }

}
