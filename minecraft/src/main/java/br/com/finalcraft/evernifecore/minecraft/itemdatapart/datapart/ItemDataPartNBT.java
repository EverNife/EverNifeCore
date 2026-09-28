package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.RegisteredPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineException;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The escape hatch: whatever the item carries that no other key has a name for.
 *
 * <p>It always means custom data, on every version. From 1.20.5 on the server keeps that under
 * {@code minecraft:custom_data} and offers a typed side as well, but a block of lines written for
 * one server has to mean the same thing on another - so the typed side got its own key instead of
 * quietly taking this one over.</p>
 *
 * <p>What another active key already writes is dropped on the way out: emitting the name inside
 * the tag as well as under {@code name:} would make a round trip write it twice. What no active key
 * answers for stays here - an enchant on a server too old for {@code enchant:} is still an enchant.</p>
 */
public class ItemDataPartNBT extends ItemDataPart<List<String>> {

    //each tag path next to the part that writes it; "display" is a compound shared with keys nobody owns
    private static final Map<String, String> OWNERS = new LinkedHashMap<>();
    static {
        OWNERS.put("display.Name", StandardParts.NAME);
        OWNERS.put("display.Lore", StandardParts.LORE);
        OWNERS.put("Damage", StandardParts.DURABILITY);
        OWNERS.put("HideFlags", StandardParts.HIDE_FLAGS);
        OWNERS.put("CustomModelData", StandardParts.CUSTOM_MODEL_DATA);
        OWNERS.put("ench", StandardParts.ENCHANT);
        OWNERS.put("Enchantments", StandardParts.ENCHANT);
    }

    private final ItemEngine engine;

    public ItemDataPartNBT(@Nonnull ItemEngine engine) {
        this.engine = engine;
    }

    /** The tag paths this hatch leaves out on its engine's runtime, because an active key writes them. */
    @Nonnull
    public Set<String> getPathsOwnedElsewhere() {
        ItemRuntime runtime = engine.getRuntime();
        Set<String> owned = new LinkedHashSet<>();
        if (runtime.isAtLeast(MCDetailedVersion.v1_20_R4)) {
            //the tag is custom data only from here on: the server keeps its own concepts in components
            return owned;
        }
        for (Map.Entry<String, String> entry : OWNERS.entrySet()) {
            if (entry.getKey().equals("Damage") && !runtime.isAtLeast(MCDetailedVersion.v1_13_R1)) {
                continue; //damage was the stack's own field before 1.13, so a tag "Damage" there is a mod's
            }
            RegisteredPart owner = engine.find(entry.getValue());
            if (owner != null && owner.isActive()) {
                owned.add(entry.getKey());
            }
        }
        return owned;
    }

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
        ReadWriteNBT display = tag.getCompound("display");
        for (String path : getPathsOwnedElsewhere()) {
            if (path.startsWith("display.")) {
                if (display != null) {
                    display.removeKey(path.substring("display.".length()));
                }
            } else {
                tag.removeKey(path);
            }
        }
        if (display != null && display.getKeys().isEmpty()) {
            tag.removeKey("display");
        }
        return tag.getKeys().isEmpty() ? null : Collections.singletonList(tag.toString());
    }

    /** Reading a whole tag twice is expensive, so this only answers when it is asked to. */
    @Override
    public int getPriority() {
        return PRIORITY_VERY_LATE;
    }

}
