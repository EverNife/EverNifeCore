package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.reflection.FieldAccessor;
import org.black_ixx.bossshop.BossShop;
import org.black_ixx.bossshop.api.BossShopAPI;
import org.black_ixx.bossshop.core.BSBuy;
import org.black_ixx.bossshop.core.BSShop;
import org.black_ixx.bossshop.inbuiltaddons.advancedshops.ActionSet;
import org.black_ixx.bossshop.inbuiltaddons.advancedshops.BSBuyAdvanced;
import org.black_ixx.bossshop.managers.ClassManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Map;

public class BossShopIntegration {

    private static BossShop bossShopPlugin = null;
    private static Boolean isPresent = null;

    public static boolean isPresent(){
        if (isPresent == null){
            bossShopPlugin = (BossShop) Bukkit.getPluginManager().getPlugin("BossShopPro");
            isPresent = bossShopPlugin != null;
        }
        return isPresent;
    }

    public static void openShop(Player player, String shopName){
        BSShop shop = getAPI().getShop(shopName);
        getAPI().openShop(player, shop);
    }

    public static BossShopAPI getAPI() {
        if (isPresent()){
            return bossShopPlugin.getAPI(); // Returns BossShopPro API
        }
        return null;
    }

    public static BossShop getBossShopPlugin(){
        return isPresent() ? bossShopPlugin : null;
    }

    private static FieldAccessor<Map<ClickType, ActionSet>> ACTIONS_FIELD = FCReflectionUtil.getField(BSBuyAdvanced.class, "actions");
    public static BSBuy cloneBSBuy(BSBuy bsBuy, String newName){
        BSBuy clone;
        if (bsBuy instanceof BSBuyAdvanced){
            BSBuyAdvanced bsBuyAdv = (BSBuyAdvanced) bsBuy;
            Map<ClickType, ActionSet> actions = ACTIONS_FIELD.get(bsBuyAdv);
            ACTIONS_FIELD.set(bsBuyAdv, null);//Temporary Remove
            clone = new BSBuyAdvanced(
                    bsBuyAdv.getRewardType(ClickType.LEFT),
                    bsBuyAdv.getPriceType(ClickType.LEFT),
                    bsBuyAdv.getReward(ClickType.LEFT),
                    bsBuyAdv.getPrice(ClickType.LEFT),
                    bsBuyAdv.getMessage(ClickType.LEFT),
                    bsBuyAdv.getInventoryLocation(),
                    bsBuyAdv.getExtraPermission(ClickType.LEFT),
                    newName,
                    bsBuyAdv.getCondition(),
                    bsBuyAdv.getInputType(ClickType.LEFT),
                    bsBuyAdv.getInputText(ClickType.LEFT),
                    null
            );
            ACTIONS_FIELD.set(bsBuyAdv, actions);//Re-Add
            ACTIONS_FIELD.set(clone, actions);//Add to Clone
        }else {
            clone = new BSBuy( //It doesn't matter the ClickType
                    bsBuy.getRewardType(ClickType.LEFT),
                    bsBuy.getPriceType(ClickType.LEFT),
                    bsBuy.getReward(ClickType.LEFT),
                    bsBuy.getPrice(ClickType.LEFT),
                    bsBuy.getMessage(ClickType.LEFT),
                    bsBuy.getInventoryLocation(),
                    bsBuy.getExtraPermission(ClickType.LEFT),
                    newName,
                    bsBuy.getCondition(),
                    bsBuy.getInputType(ClickType.LEFT),
                    bsBuy.getInputText(ClickType.LEFT)
            );
        }
        clone.setItem(bsBuy.getItem().clone(), bsBuy.isItemFix());
        return clone;
    }

    public static ClassManager getClassManager(){
        return ClassManager.manager;
    }

}
