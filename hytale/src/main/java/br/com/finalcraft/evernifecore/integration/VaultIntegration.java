package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import net.cfh.vault.VaultUnlockedServicesManager;
import net.milkbowl.vault2.economy.Economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public class VaultIntegration {

    public static Economy econ = null;

    public static void initialize() {
        if (!FCReflectionUtil.isClassLoaded("net.cfh.vault.VaultUnlocked")){
            EverNifeCore.getLog().warning("VaultUnlocked plugin was not found! EverNifeCore need Vault to manage economy transactions!");
            return;
        }
        try {
            setupEconomy();
        }catch (Throwable e){
            EverNifeCore.getLog().warning("Vault seems to be present but there is no Economic plugin present!");
            e.printStackTrace();
        }
    }

    private static void setupEconomy() {
        Optional<Economy> economy = VaultUnlockedServicesManager.get().economy();
        if (!economy.isPresent()){
            throw new IllegalStateException("No Economy plugin found!");
        }
    }

    public static void ecoGive(UUID playerUuid, double amount){
        getEcon().deposit("", playerUuid, BigDecimal.valueOf(amount));
    }

    public static void ecoSet(UUID playerUuid, double amount){
        double current = econ.getBalance("", playerUuid).doubleValue();
        double needed = amount - current;
        if (needed == 0) return;
        if (needed > 0){
            ecoGive(playerUuid,needed);
        }else {
            ecoTake(playerUuid,-needed);
        }
    }

    public static boolean ecoTake(UUID playerUuid, double amount){
        if (amount <= 0){
            return true;
        }
        if (!ecoHasEnough(playerUuid,amount)){
            return false;
        }
        getEcon().withdraw("", playerUuid, BigDecimal.valueOf(amount));
        return true;
    }

    public static boolean ecoHasEnough(UUID playerUuid, double amount){
        if (amount <= 0){
            return true;
        }
        return getEcon().has("", playerUuid, BigDecimal.valueOf(amount));
    }

    public static double ecoGet(UUID playerUuid){
        return getEcon().balance("", playerUuid).doubleValue();
    }

    public static Economy getEcon() {
        if (econ == null) {
            initialize();
        }

        return econ;
    }
}
