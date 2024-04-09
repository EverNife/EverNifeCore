package br.com.finalcraft.evernifecore.integration.bossshop.datapart;

import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.managers.item.ItemDataPart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BSItemDataPartNBT extends ItemDataPart {

    public static final String NBT_TAG = "ec_temporary_tag_to_be_removed_later";

    final Pattern pattern = Pattern.compile("%\\w+%");

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        // Use Matcher to find the pattern in the input string
        Matcher matcher = pattern.matcher(argument);

        if (matcher.find()) {
            //We have at least one placeholder here, lets delay the apply of the NBT for later, only apply on
            NBTContainer nbtContainer = FCNBTUtil.getFrom("{}");
            nbtContainer.setString(NBT_TAG, argument);
            argument = nbtContainer.toString();
        }

        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.transform(item, used_name, argument);
    }

    @Override
    public int getPriority() {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.getPriority();
    }

    @Override
    public boolean removeSpaces() {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.removeSpaces();
    }

    @Override
    public String[] createNames() {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.createNames();
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        return br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart.NBT.read(i, output);
    }

    @Override
    public boolean isSimilar(ItemStack shop_item, ItemStack player_item, BSBuy buy, Player p) {
        return true; //Too expensive to check
    }

}
