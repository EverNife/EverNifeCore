package br.com.finalcraft.evernifecore.integration;

import org.black_ixx.bossshop.BossShop;
import org.black_ixx.bossshop.api.BossShopAPI;
import org.black_ixx.bossshop.core.BSShop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BossShopIntegration {

    private static BossShop bossShopPlugin = null;
    private static Boolean isPresent = null;

    private static boolean isPresent(){
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

}
