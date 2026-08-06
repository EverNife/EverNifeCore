package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The escape hatch: whatever the item carries that no other key has a name for.
 *
 * <p>It always means custom data, on every version. From 1.20.5 on the server keeps that under
 * {@code minecraft:custom_data} and offers a typed side as well, but a block of lines written for
 * one server has to mean the same thing on another - so the typed side got its own key instead of
 * quietly taking this one over.</p>
 *
 * <p>What other keys already own is dropped on the way out. Emitting the name inside the tag as
 * well as under {@code name:} would make a round trip write it twice.</p>
 */
public class ItemDataPartNBT extends ItemDataPart<List<String>> {

    private static final String[] OWNED_ELSEWHERE = {"display", "Damage", "HideFlags", "ench", "Enchantments"};

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "nbt";
    }

    @Nonnull
    @Override
    public List<String> parse(@Nonnull String argument) throws ItemLineException {
        String snbt = argument.trim();
        if (!snbt.startsWith("{") || !snbt.endsWith("}")) {
            throw ItemLineException.expecting(argument, "a compound in SNBT, braces included",
                    "{CustomModelData:1042}");
        }
        return Collections.singletonList(snbt);
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull List<String> value) {
        return new ArrayList<>(value);
    }

    @Nonnull
    @Override
    public List<String> merge(@Nonnull List<String> previous, @Nonnull List<String> next) {
        List<String> joined = new ArrayList<>(previous);
        joined.addAll(next);
        return joined;
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull List<String> value, @Nonnull ItemStack item) {
        NbtDoor.custom().modifyBatch(item, tag -> {
            for (String snbt : value) {
                tag.mergeCompound(NbtDoor.parse(snbt));
            }
        });
        return item;
    }

    @Nullable
    @Override
    public List<String> extract(@Nonnull ItemStack item) {
        ReadWriteNBT tag = NbtDoor.custom().snapshot(item);
        for (String owned : OWNED_ELSEWHERE) {
            tag.removeKey(owned);
        }
        return tag.getKeys().isEmpty() ? null : Collections.singletonList(tag.toString());
    }

    /** Reading a whole tag twice is expensive, so this only answers when it is asked to. */
    @Override
    public int getPriority() {
        return PRIORITY_VERY_LATE;
    }

}
