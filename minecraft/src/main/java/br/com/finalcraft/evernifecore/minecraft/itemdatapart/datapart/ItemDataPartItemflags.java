package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the tooltip hides.
 *
 * <p>{@code #} still joins several in one line here, because a flag name cannot contain one - which
 * is the only test a separator has to pass.</p>
 */
public class ItemDataPartItemflags extends ItemDataPart<Set<ItemFlag>> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "hideflags";
    }

    @Nonnull
    @Override
    public Set<ItemFlag> parse(@Nonnull String argument) throws ItemLineException {
        if (argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("all")) {
            return EnumSet.allOf(ItemFlag.class);
        }

        Set<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);
        for (String written : argument.split("#")) {
            String name = written.trim().toUpperCase().replace(' ', '_');
            if (name.isEmpty()) {
                continue;
            }
            if (!name.startsWith("HIDE_")) {
                name = "HIDE_" + name;
            }
            try {
                flags.add(ItemFlag.valueOf(name));
            } catch (IllegalArgumentException unknown) {
                throw new ItemLineException("'" + written.trim() + "' is not a flag this server has. "
                        + "Write 'all', or one or more of " + names() + ", joined with '#'.");
            }
        }
        return flags;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull Set<ItemFlag> value) {
        List<String> arguments = new ArrayList<>();
        if (value.size() == ItemFlag.values().length) {
            arguments.add("all");
            return arguments;
        }
        for (ItemFlag flag : value) {
            arguments.add(flag.name());
        }
        return arguments;
    }

    @Nonnull
    @Override
    public Set<ItemFlag> merge(@Nonnull Set<ItemFlag> previous, @Nonnull Set<ItemFlag> next) {
        Set<ItemFlag> joined = EnumSet.noneOf(ItemFlag.class);
        joined.addAll(previous);
        joined.addAll(next);
        return joined;
    }

    @Nonnull
    @Override
    public ItemStack apply(@Nonnull Set<ItemFlag> value, @Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(value.toArray(new ItemFlag[0]));
        item.setItemMeta(meta);
        return item;
    }

    @Nullable
    @Override
    public Set<ItemFlag> extract(@Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        Set<ItemFlag> flags = meta.getItemFlags();
        if (flags == null || flags.isEmpty()) {
            return null;
        }
        Set<ItemFlag> copy = EnumSet.noneOf(ItemFlag.class);
        copy.addAll(flags);
        return copy;
    }

    @Override
    public int getPriority() {
        return PRIORITY_LATE;
    }

    private static String names() {
        List<String> names = new ArrayList<>();
        for (ItemFlag flag : ItemFlag.values()) {
            names.add(flag.name());
        }
        return String.join(", ", names);
    }

}
