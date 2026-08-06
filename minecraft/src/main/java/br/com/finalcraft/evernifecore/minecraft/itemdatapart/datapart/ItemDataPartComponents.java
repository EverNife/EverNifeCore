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
 * The typed side of the item, as 1.20.5 and up model it.
 *
 * <p>This is where {@code nbt:} does not reach: a component is not free-form data the server keeps
 * for whoever wrote it, it is a field the game itself understands. A server that has no components
 * refuses this key by name and says so, instead of writing the data somewhere it will never be
 * read from.</p>
 */
public class ItemDataPartComponents extends ItemDataPart<List<String>> {

    private static final String[] OWNED_ELSEWHERE = {
            "minecraft:custom_data", "minecraft:custom_name", "minecraft:lore",
            "minecraft:damage", "minecraft:enchantments", "minecraft:custom_model_data"
    };

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "components";
    }

    @Nonnull
    @Override
    public List<String> parse(@Nonnull String argument) throws ItemLineException {
        String snbt = argument.trim();
        if (!snbt.startsWith("{") || !snbt.endsWith("}")) {
            throw ItemLineException.expecting(argument, "a compound of components in SNBT, braces included",
                    "{\"minecraft:max_stack_size\":1}");
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
        NbtDoor.components().modifyBatch(item, components -> {
            for (String snbt : value) {
                components.mergeCompound(NbtDoor.parse(snbt));
            }
        });
        return item;
    }

    @Nullable
    @Override
    public List<String> extract(@Nonnull ItemStack item) {
        ReadWriteNBT components = NbtDoor.components().snapshot(item);
        for (String owned : OWNED_ELSEWHERE) {
            components.removeKey(owned);
        }
        return components.getKeys().isEmpty() ? null : Collections.singletonList(components.toString());
    }

    @Override
    public int getPriority() {
        return PRIORITY_VERY_LATE;
    }

}
