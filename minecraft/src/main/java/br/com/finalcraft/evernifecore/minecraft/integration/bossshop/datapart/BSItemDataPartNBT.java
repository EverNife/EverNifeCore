package br.com.finalcraft.evernifecore.minecraft.integration.bossshop.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.RegisteredPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import org.black_ixx.bossshop.core.BSBuy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BossShopPro's own nbt key, answered by this library's engine so a shop reads and writes the same
 * tag text every other item-data block does.
 *
 * <p>A shop's value may hold a {@code %placeholder%} that only means something once a buyer is
 * known, and SNBT with a placeholder in it does not parse. So the argument is parked verbatim under
 * a tag of its own and the real write happens later, when the placeholder can be resolved.</p>
 */
//the plugin's own ItemDataPart is spelled out here because this file names ours as well
public class BSItemDataPartNBT extends org.black_ixx.bossshop.managers.item.ItemDataPart {

    public static final String NBT_TAG = "ec_temporary_tag_to_be_removed_later";

    final Pattern pattern = Pattern.compile("%\\w+%");

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        Matcher matcher = pattern.matcher(argument);

        if (matcher.find()) {
            NBTContainer parked = FCNBTUtil.getFrom("{}");
            parked.setString(NBT_TAG, argument);
            argument = parked.toString();
        }

        return ItemEngine.get().transform(item,
                Collections.singletonList(StandardParts.NBT + ":" + argument));
    }

    @Override
    public int getPriority() {
        RegisteredPart nbt = nbtPart();
        return nbt == null ? ItemDataPart.PRIORITY_VERY_LATE : nbt.getPriority();
    }

    @Override
    public boolean removeSpaces() {
        return false; //SNBT is whitespace significant inside its strings
    }

    @Override
    public String[] createNames() {
        RegisteredPart nbt = nbtPart();
        return nbt == null ? new String[]{StandardParts.NBT} : nbt.getSpellings();
    }

    /**
     * The one line this key answers for, taken from the part that owns it.
     *
     * <p>Describing the whole item to keep a single line makes every other part read what nobody
     * asked it for, and it spends the one warning a reduced runtime gets to give.</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> read(ItemStack i, List<String> output) {
        RegisteredPart nbt = nbtPart();
        if (nbt == null || !nbt.isActive()) {
            return output;
        }
        ItemDataPart<Object> part = (ItemDataPart<Object>) nbt.getPart();
        Object value = part.extract(i);
        if (value != null) {
            for (String argument : part.format(value)) {
                output.add(nbt.getKey() + ":" + argument);
            }
        }
        return output;
    }

    @Override
    public boolean isSimilar(ItemStack shop_item, ItemStack player_item, BSBuy buy, Player p) {
        return true; //Too expensive to check
    }

    /** {@code null} when this engine carries no nbt part at all, which every caller answers for. */
    private static RegisteredPart nbtPart() {
        return ItemEngine.get().find(StandardParts.NBT);
    }

}
