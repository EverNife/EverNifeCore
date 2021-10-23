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
            EverNifeCore.warning("Vault seems to be present but there is not Economic plugin present!");
            EverNifeCore.warning("You might take a look at: https://github.com/evernife/FinalEconomy");
            e.printStackTrace();
        }
    }

    private static boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = EverNifeCore.instance.getServer().getServicesManager().getRegistration(Economy.class);
        econ = rsp.getProvider();
        return econ != null;
    }

    public static void ecoGive(OfflinePlayer player, double amout){
        econ.depositPlayer(player,amout);
    }

    public static boolean ecoTake(OfflinePlayer player, double amout){
        if (amout <= 0){
            return true;
        }
        if (!ecoHasEnough(player,amout)){
            return false;
        }
        econ.withdrawPlayer(player,amout);
        return true;
    }

    public static boolean ecoHasEnough(OfflinePlayer player,double amout){
        if (amout <= 0){
            return true;
        }
        return econ.has(player,amout);
    }

    public static double getBalance(OfflinePlayer player){
        return econ.getBalance(player);
    }
}
