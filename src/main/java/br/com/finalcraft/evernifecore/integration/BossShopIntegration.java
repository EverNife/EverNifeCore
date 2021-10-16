package br.com.finalcraft.evernifecore.integration;

import org.black_ixx.bossshop.BossShop;
import org.black_ixx.bossshop.api.BossShopAPI;
import org.black_ixx.bossshop.core.BSShop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BossShopIntegration {

    private static final BossShop bossShop = null;

    private static Boolean isPresent = null;
    private static boolean isPresent(){
        if (isPresent == null){
            isPresent = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? true : false;
        }
        return isPresent;
    }

    public static void openShop(Player player, String shopName){
        BSShop shop = getAPI().getShop(shopName);
        getAPI().openShop(player, shop);
    }

    public static BossShopAPI getAPI() {
        if (isPresent()){
            return bossShop.getAPI(); // Returns BossShopPro API
        }
        return null;
    }

}
