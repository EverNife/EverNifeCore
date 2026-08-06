package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemLineException;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import br.com.finalcraft.everylibs.util.FCInputReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;

/**
 * What the item is: a bukkit {@code NAME[:durability]} or a namespaced {@code mod:item}.
 *
 * <p>The value is the identifier as written, because only the running server can say what a
 * namespaced one means - and holding the text until then is what lets a recipe exist off a server.</p>
 */
public class ItemDataPartMaterial extends ItemDataPart<String> {

    @Nonnull
    @Override
    public String getCanonicalKey() {
        return "type";
    }

    @Nonnull
    @Override
    public String parse(@Nonnull String argument) throws ItemLineException {
        String identifier = argument.replace(" ", "");
        if (identifier.isEmpty()) {
            throw ItemLineException.expecting(argument, "a material name, optionally with a data value, "
                    + "or a namespaced identifier", "DIAMOND_SWORD");
        }
        return identifier;
    }

    @Nonnull
    @Override
    public List<String> format(@Nonnull String value) {
        return Collections.singletonList(value);
    }

    /**
     * Retypes the item in place, which is what keeps everything else it carries.
     *
     * <p>A namespaced identifier is the one exception: a modded item is defined by its own tag, so
     * the resolved item replaces the old one and only the stack size crosses over.</p>
     */
    @Nonnull
    @Override
    public ItemStack apply(@Nonnull String value, @Nonnull ItemStack item) {
        String[] split = value.split(":", 2);
        Integer dataValue = split.length == 2 ? FCInputReader.parseInt(split[1], null) : null;

        if (split.length == 2 && dataValue == null) {
            ItemStack resolved = FCItemUtils.fromMinecraftIdentifier(value);
            resolved.setAmount(item.getAmount());
            return resolved;
        }

        Material material = FCMaterialUtil.parseMaterial(split[0]);
        if (material == null) {
            throw new ItemLineException("'" + value + "' is not a material this server has. Write a name "
                    + "from the server's own list (DIAMOND_SWORD), a name with a data value (WOOL:14), "
                    + "or a namespaced identifier a mod registered (mymod:cool_item).");
        }
        item.setType(material);
        if (dataValue != null) {
            item.setDurability(dataValue.shortValue());
        }
        return item;
    }

    @Nullable
    @Override
    public String extract(@Nonnull ItemStack item) {
        return item.getType().name();
    }

    @Override
    public int getPriority() {
        return PRIORITY_MOST_EARLY;
    }

}
