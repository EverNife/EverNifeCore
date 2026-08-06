package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemLineException;
import br.com.finalcraft.everylibs.util.FCInputReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * Damage on a tool, or the data value that used to pick a colour before 1.13.
 *
 * <p>One call answers for both eras: {@code ItemStack.getDurability} is the field itself on the old
 * versions and the metadata's damage on the new ones, and which one it is has never been this
 * side's business.</p>
 */
public class ItemDataPartDurability extends ItemDataPart<Integer> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "durability";
    }

    @Nonnull
    @Override
    public Integer parse(@Nonnull String argument) throws ItemLineException {
        Integer damage = FCInputReader.parseInt(argument.replace(" ", ""), null);
        if (damage == null) {
            throw ItemLineException.expecting(argument, "a whole number of damage points", "200");
        }
        return damage;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull Integer value) {
        return Collections.singletonList(String.valueOf(value));
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull Integer value, @Nonnull ItemStack item) {
        item.setDurability(value.shortValue());
        return item;
    }

    /** Undamaged is the default of every item, so it is not worth a line. */
    @Nullable
    @Override
    public Integer extract(@Nonnull ItemStack item) {
        short durability = item.getDurability();
        return durability == 0 ? null : (int) durability;
    }

    @Override
    public int getPriority() {
        return PRIORITY_EARLY;
    }

}
