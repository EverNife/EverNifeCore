package br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemDataPartNBT extends ItemDataPart {

    @Override
    public ItemStack transform(ItemStack item, String used_name, String argument) {
        try {
            return FCItemFactory.from(item)
                    .setNbt(FCNBTUtil.getFrom(argument.trim()))
                    .build();
        }catch (Exception e){
            e.printStackTrace();

            String itemIdentifier;

            try {
                itemIdentifier = FCItemUtils.getMinecraftIdentifier(item);
            } catch (Exception e2) {
                e2.printStackTrace();
                itemIdentifier = "[ITEM_IS_CORRUPTED]";
            }

            throw new RuntimeException(String.format(
                    "[EverNifeCore] Failed to transform NBT data for the item" +
                            "\n  - itemIdentifier: %s" +
                            "\n  - itemNBT: %s" +
                            "\n  - used_name: %s",
                    itemIdentifier, argument, used_name
            ));
        }
    }

    @Override
    public boolean isSimilar(ItemStack base_item, ItemStack other_item) {
        return true; //To expensive to check
    }

    // Opt-in NBT comparison: the 2-arg default stays a cheap no-op (returns true); this path normalizes
    // both items' NBT the same way read() does (drop display, and on >=1.13 Damage/HideFlags) and compares
    // them. Two items with no NBT normalize equal, so they are considered similar.
    public boolean isSimilar(ItemStack base_item, ItemStack other_item, boolean considerNBT) {
        if (!considerNBT) {
            return true;
        }
        return normalizedNbt(base_item).equals(normalizedNbt(other_item));
    }

    private String normalizedNbt(ItemStack item) {
        NBTCompound compound = FCNBTUtil.getFrom(FCNBTUtil.getFrom(item).toString());
        if (!FCNBTUtil.isEmpty(compound)) {
            compound.removeKey("display");
            if (MCVersion.isHigherEquals(MCVersion.v1_13)) {
                compound.removeKey("Damage");
                compound.removeKey("HideFlags");
            }
        }
        return compound.toString();
    }

    @Override
    public List<String> read(ItemStack i, List<String> output) {
        NBTCompound compound = FCNBTUtil.getFrom( //Clone it because we may need to remove the "display" tag, and the "Damage" tag as well
                FCNBTUtil.getFrom(i).toString()
        );

        if (!FCNBTUtil.isEmpty(compound)){
            compound.removeKey("display");//Remove LORE and DisplayName
            if (MCVersion.isHigherEquals(MCVersion.v1_13)){
                compound.removeKey("Damage");//Remove Damage key
                compound.removeKey("HideFlags");//Remove Flags key
            }
            if (!FCNBTUtil.isEmpty(compound)){
                output.add("nbt: " + compound.toString());
            }
        }

        return output;
    }

    @Override
    public int getPriority() {
        return 5; // AFTER "PRIORITY_MOST_EARLY"
    }

    @Override
    public boolean removeSpaces() {
        return false;
    }

    @Override
    public String[] createNames() {
        return new String[]{"nbt", "rawnbt"};
    }

}
