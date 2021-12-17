package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.EverNifeCore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultIntegration {

    public static Economy econ = null;

    public static void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")){
            EverNifeCore.warning("Vault plugin was not found! EverNifeCore need vault to manage economy transactions!");
            return;
        }
        try {
            setupEconomy();
        }catch (Throwable e){
            EverNifeCore.warning("Vault seems to be present but there is no Economic plugin present!");
            EverNifeCore.warning("You might want to take a look at: https://www.spigotmc.org/resources/finaleconomy.97740/");
            e.printStackTrace();
        }
    }

    private static boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = EverNifeCore.instance.getServer().getServicesManager().getRegistration(Economy.class);
        econ = rsp.getProvider();
        return econ != null;
    }

    public static void ecoGive(OfflinePlayer player, double amount){
        econ.depositPlayer(player,amount);
    }

    public static void ecoSet(OfflinePlayer player, double amount){
        double current = econ.getBalance(player);
        double needed = amount - current;
        if (needed == 0) return;
        if (needed > 0){
            ecoGive(player,needed);
        }else {
            ecoTake(player,-needed);
        }
    }

    public static boolean ecoTake(OfflinePlayer player, double amount){
        if (amount <= 0){
            return true;
        }
        if (!ecoHasEnough(player,amount)){
            return false;
        }
        econ.withdrawPlayer(player,amount);
        return true;
    }

    public static boolean ecoHasEnough(OfflinePlayer player,double amount){
        if (amount <= 0){
            return true;
        }
        return econ.has(player,amount);
    }

    public static double ecoGet(OfflinePlayer player){
        return econ.getBalance(player);
    }
}
