package br.com.finalcraft.evernifecore.integration.bossshop.customizer;

import br.com.finalcraft.evernifecore.integration.bossshop.datapart.BSItemDataPartNBT;
import br.com.finalcraft.evernifecore.integration.bossshop.shops.IECShop;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.core.BSShop;
import org.black_ixx.bossshop.core.BSShopHolder;
import org.black_ixx.bossshop.managers.ClassManager;
import org.black_ixx.bossshop.managers.item.ItemStackTranslator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ECItemStackTranslator extends ItemStackTranslator {

    @Override
    public ItemStack translateItemStack(BSBuy buy, BSShop shop, BSShopHolder holder, ItemStack item, Player target, boolean final_version) {
        ItemStack itemStack = super.translateItemStack(buy, shop, holder, item, target, final_version); //Do Default Tranlation

        if (target != null){
            NBTItem itemNBT = FCNBTUtil.getFrom(itemStack);

            if (!itemNBT.isEmpty() && itemNBT.hasTag(BSItemDataPartNBT.NBT_TAG)){
                String nbtString = itemNBT.getString(BSItemDataPartNBT.NBT_TAG);
                String parsedNbtString = ClassManager.manager.getStringManager().transform(nbtString, buy, shop, holder, target);

                try {
                    NBTContainer nbtContent = FCNBTUtil.getFrom(parsedNbtString);

                    itemStack = FCItemFactory.from(itemStack)
                            .setNbt(nbtCompound -> {
                                nbtCompound.removeKey(BSItemDataPartNBT.NBT_TAG);
                                nbtCompound.mergeCompound(nbtContent);
                            }).build();
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
                            "[EverNifeCore] Failed to transform NBT data [At LaterStage] for the item" +
                                    "\n  - itemIdentifier: %s" +
                                    "\n  - parsedNbtString: %s",
                            itemIdentifier, parsedNbtString
                    ));
                }
            }
        }

        if (shop instanceof IECShop){ //IF ECshop, fire last customization!
            itemStack = ((IECShop) shop).finalizeTranslateItemStack(buy, shop, holder, item, target, final_version);
        }

        return itemStack;
    }

}
