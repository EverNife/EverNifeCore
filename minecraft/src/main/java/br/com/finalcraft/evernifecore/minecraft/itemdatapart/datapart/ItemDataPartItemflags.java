package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
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
 * <p>A block of {@code hideflags:} lines is the whole answer: what it names is hidden and what it
 * does not name is shown, so a line can take a flag away as well as add one.</p>
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

    /**
     * Every flag the argument names, minus the ones this server has no name for.
     *
     * <p>A fork missing a single flag is not a mistake in the file: the line keeps the flags that do
     * exist and says out loud which one it dropped. A line whose flags are all unknown here is
     * refused instead - hiding nothing is a decision, and no file ever meant to say it that way.</p>
     */
    @Nonnull
    @Override
    public Set<ItemFlag> parse(@Nonnull String argument) throws ItemLineException {
        if (argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("all")) {
            return EnumSet.allOf(ItemFlag.class);
        }
        if (argument.trim().isEmpty()) {
            throw new ItemLineException("A 'hideflags:' line with nothing after it hides nothing, and "
                    + "an empty value never says whether that was meant. Write 'all', or one or more of "
                    + names() + ", joined with '#' - or drop the line.");
        }

        Set<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);
        List<String> unknown = new ArrayList<>();
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
            } catch (IllegalArgumentException absentHere) {
                unknown.add(written.trim());
            }
        }
        if (flags.isEmpty()) {
            throw new ItemLineException("'" + argument + "' is not a flag this server has. "
                    + "Write 'all', or one or more of " + names() + ", joined with '#'.");
        }
        if (!unknown.isEmpty()) {
            ItemEngine.warn("This server has no flag called " + String.join(", ", unknown)
                    + ", so 'hideflags:" + argument + "' was applied without it. The flags that exist "
                    + "here are " + names() + ".");
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

    /** The set is the answer, so what it leaves out is shown again - the same rule the lore follows. */
    @Nonnull
    @Override
    public ItemStack apply(@Nonnull Set<ItemFlag> value, @Nonnull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.removeItemFlags(meta.getItemFlags().toArray(new ItemFlag[0]));
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
