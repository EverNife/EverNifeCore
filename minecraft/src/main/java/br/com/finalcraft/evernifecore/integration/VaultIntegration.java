package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.UUID;

public class VaultIntegration {

    public static IVaultEconomy vaultEconomy = null;

    public static void initialize() {

        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")){
            EverNifeCore.getLog().warning("Vault plugin was not found! EverNifeCore need Vault to manage economy transactions!");
            return;
        }

        try {
            setupEconomy();
        }catch (Throwable e){
            EverNifeCore.getLog().warning("Vault seems to be present but there is no Economy plugin present!");

            if (Bukkit.getPluginManager().isPluginEnabled("CMI")){
                EverNifeCore.getLog().warning("CMI was found, but it's economy module is not enabled i think, you might want to take a look at: https://www.spigotmc.org/resources/cmi.3742/");
                EverNifeCore.getLog().warning("Read their description to learn how to enable CMI Economy module.");
            }

            EverNifeCore.getLog().warning("If you need a simple economy, you might want to take a look at: https://www.spigotmc.org/resources/finaleconomy.97740/");
            e.printStackTrace();
        }
    }

    private static void setupEconomy() {

        if (FCReflectionUtil.isClassLoaded("net.milkbowl.vault2.economy.Economy")){
            Class economyV2Class = FCReflectionUtil.getClass("net.milkbowl.vault2.economy.Economy");
            RegisteredServiceProvider<?> registration2 = EverNifeCore.instance.getServer().getServicesManager().getRegistration(economyV2Class);
            if (registration2 != null && registration2.getProvider() != null){
                vaultEconomy = new VaultEconV2(registration2.getProvider());
                return;
            }
        }

        if (FCReflectionUtil.isClassLoaded("net.milkbowl.vault.economy.Economy")){
            Class economyV1Class = FCReflectionUtil.getClass("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration1 = EverNifeCore.instance.getServer().getServicesManager().getRegistration(economyV1Class);
            if (registration1 != null && registration1.getProvider() != null){
                vaultEconomy = new VaultEconV1(registration1.getProvider());
                return;
            }
        }

        throw new IllegalStateException("No Economy plugin found on the Server!");
    }

    public static IVaultEconomy getEcon() {
        if (vaultEconomy == null) {
            initialize();
        }

        return vaultEconomy;
    }

    public static interface IVaultEconomy<ECONOMY> {

        public ECONOMY getHandle();

        public boolean ecoGive(OfflinePlayer player, BigDecimal amount);

        public boolean ecoGive(OfflinePlayer player, double amount);

        public boolean ecoTake(OfflinePlayer player, BigDecimal amount);

        public boolean ecoTake(OfflinePlayer player, double amount);

        public boolean ecoSet(OfflinePlayer player, BigDecimal amount);

        public boolean ecoSet(OfflinePlayer player, double amount);

        public double ecoGet(OfflinePlayer player);

        public BigDecimal ecoGetInBigDecimal(OfflinePlayer player);

        public boolean ecoHasEnough(OfflinePlayer player, BigDecimal amount);

        public boolean ecoHasEnough(OfflinePlayer player, double amount);

        public boolean ecoGive(UUID playerUUID, BigDecimal amount);

        public boolean ecoGive(UUID playerUUID, double amount);

        public boolean ecoTake(UUID playerUUID, BigDecimal amount);

        public boolean ecoTake(UUID playerUUID, double amount);

        public boolean ecoSet(UUID playerUUID, BigDecimal amount);

        public boolean ecoSet(UUID playerUUID, double amount);

        public double ecoGet(UUID playerUUID);

        public BigDecimal ecoGetInBigDecimal(UUID playerUUID);

        public boolean ecoHasEnough(UUID playerUUID, BigDecimal amount);

        public boolean ecoHasEnough(UUID playerUUID, double amount);

    }

    public static class VaultEconV1 implements IVaultEconomy<Economy> {

        private Economy economy;

        public VaultEconV1(Object economy) {
            this.economy = (Economy) economy;
        }

        @Override
        public Economy getHandle() {
            return economy;
        }

        @Override
        public boolean ecoGive(OfflinePlayer player, BigDecimal amount) {
            return ecoGive(player, amount.doubleValue());
        }

        @Override
        public boolean ecoGive(OfflinePlayer player, double amount) {
            return economy.depositPlayer(player, amount).transactionSuccess();
        }

        @Override
        public boolean ecoTake(OfflinePlayer player, BigDecimal amount) {
            return ecoTake(player, amount.doubleValue());
        }

        @Override
        public boolean ecoTake(OfflinePlayer player, double amount) {
            if (amount <= 0){
                return true;
            }
            if (!ecoHasEnough(player, amount)){
                return false;
            }
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }

        @Override
        public boolean ecoSet(OfflinePlayer player, BigDecimal amount) {
            BigDecimal current = ecoGetInBigDecimal(player);
            BigDecimal needed = amount.subtract(current);
            int cmp = needed.compareTo(BigDecimal.ZERO);
            if (cmp == 0) {
                return false;
            }
            if (cmp > 0) {
                return ecoGive(player, needed);
            } else {
                return ecoTake(player, needed.negate());
            }
        }

        @Override
        public boolean ecoSet(OfflinePlayer player, double amount) {
            double current = ecoGet(player);
            double needed = amount - current;
            if (needed == 0) {
                return false;
            }
            if (needed > 0) {
                return ecoGive(player, needed);
            } else {
                return ecoTake(player, -needed);
            }
        }

        @Override
        public double ecoGet(OfflinePlayer player) {
            return economy.getBalance(player);
        }

        @Override
        public BigDecimal ecoGetInBigDecimal(OfflinePlayer player) {
            return BigDecimal.valueOf(economy.getBalance(player));
        }

        @Override
        public boolean ecoHasEnough(OfflinePlayer player, BigDecimal amount) {
            return ecoHasEnough(player, amount.doubleValue());
        }

        @Override
        public boolean ecoHasEnough(OfflinePlayer player, double amount) {
            if (amount <= 0){
                return true;
            }
            return economy.has(player, amount);
        }

        @Override
        public boolean ecoGive(UUID playerUUID, BigDecimal amount) {
            return ecoGive(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public boolean ecoGive(UUID playerUUID, double amount) {
            return ecoGive(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public boolean ecoTake(UUID playerUUID, BigDecimal amount) {
            return ecoTake(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public boolean ecoTake(UUID playerUUID, double amount) {
            return ecoTake(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public boolean ecoSet(UUID playerUUID, BigDecimal amount) {
            return ecoSet(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public boolean ecoSet(UUID playerUUID, double amount) {
            return ecoSet(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public double ecoGet(UUID playerUUID) {
            return ecoGet(Bukkit.getOfflinePlayer(playerUUID));
        }

        @Override
        public BigDecimal ecoGetInBigDecimal(UUID playerUUID) {
            return BigDecimal.valueOf(ecoGet(Bukkit.getOfflinePlayer(playerUUID)));
        }

        @Override
        public boolean ecoHasEnough(UUID playerUUID, BigDecimal amount) {
            return ecoHasEnough(Bukkit.getOfflinePlayer(playerUUID), amount);
        }

        @Override
        public boolean ecoHasEnough(UUID playerUUID, double amount) {
            return ecoHasEnough(Bukkit.getOfflinePlayer(playerUUID), amount);
        }
    }

    public static class VaultEconV2 implements IVaultEconomy<net.milkbowl.vault2.economy.Economy> {

        private net.milkbowl.vault2.economy.Economy economyV2;

        public VaultEconV2(Object economyV2) {
            this.economyV2 = (net.milkbowl.vault2.economy.Economy) economyV2;
        }

        @Override
        public net.milkbowl.vault2.economy.Economy getHandle() {
            return economyV2;
        }

        @Override
        public boolean ecoGive(OfflinePlayer player, BigDecimal amount) {
            return this.ecoGive(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoGive(OfflinePlayer player, double amount) {
            return this.ecoGive(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoTake(OfflinePlayer player, BigDecimal amount) {
            return this.ecoTake(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoTake(OfflinePlayer player, double amount) {
            return this.ecoTake(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoSet(OfflinePlayer player, BigDecimal amount) {
            return this.ecoSet(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoSet(OfflinePlayer player, double amount) {
            return this.ecoSet(player.getUniqueId(), amount);
        }

        @Override
        public double ecoGet(OfflinePlayer player) {
            return this.ecoGet(player.getUniqueId());
        }

        @Override
        public BigDecimal ecoGetInBigDecimal(OfflinePlayer player) {
            return this.ecoGetInBigDecimal(player.getUniqueId());
        }

        @Override
        public boolean ecoHasEnough(OfflinePlayer player, BigDecimal amount) {
            return this.ecoHasEnough(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoHasEnough(OfflinePlayer player, double amount) {
            return this.ecoHasEnough(player.getUniqueId(), amount);
        }

        @Override
        public boolean ecoGive(UUID playerUUID, BigDecimal amount) {
            return economyV2.deposit(EverNifeCore.instance.getName(), playerUUID, amount).transactionSuccess();
        }

        @Override
        public boolean ecoGive(UUID playerUUID, double amount) {
            return ecoGive(playerUUID, BigDecimal.valueOf(amount));
        }

        @Override
        public boolean ecoTake(UUID playerUUID, BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0){
                return true;
            }
            if (!ecoHasEnough(playerUUID, amount)){
                return false;
            }
            return economyV2.withdraw(EverNifeCore.instance.getName(), playerUUID, amount).transactionSuccess();
        }

        @Override
        public boolean ecoTake(UUID playerUUID, double amount) {
            return ecoTake(playerUUID, BigDecimal.valueOf(amount));
        }

        @Override
        public boolean ecoSet(UUID playerUUID, BigDecimal amount) {
            return economyV2.set(EverNifeCore.instance.getName(), playerUUID, amount).transactionSuccess();
        }

        @Override
        public boolean ecoSet(UUID playerUUID, double amount) {
            return ecoSet(playerUUID, BigDecimal.valueOf(amount));
        }

        @Override
        public double ecoGet(UUID playerUUID) {
            return economyV2.balance(EverNifeCore.instance.getName(), playerUUID).doubleValue();
        }

        @Override
        public BigDecimal ecoGetInBigDecimal(UUID playerUUID) {
            return economyV2.balance(EverNifeCore.instance.getName(), playerUUID);
        }

        @Override
        public boolean ecoHasEnough(UUID playerUUID, BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0){
                return true;
            }
            return economyV2.has(EverNifeCore.instance.getName(), playerUUID, amount);
        }

        @Override
        public boolean ecoHasEnough(UUID playerUUID, double amount) {
            return ecoHasEnough(playerUUID, BigDecimal.valueOf(amount));
        }
    }


}
