package br.com.finalcraft.evernifecore.integration;

import org.black_ixx.bossshop.BossShop;
import org.black_ixx.bossshop.api.BossShopAPI;
import org.bukkit.Bukkit;

public class BossShopIntegration {

    private static final BossShop bossShop = null;

    private static Boolean isPresent = null;
    private static boolean isPresent(){
        if (isPresent == null){
            isPresent = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? true : false;
        }
        return isPresent;
    }

    public static BossShopAPI getAPI() {
        if (isPresent()){
            return bossShop.getAPI(); // Returns BossShopPro API
        }
        return null;
    }

}
