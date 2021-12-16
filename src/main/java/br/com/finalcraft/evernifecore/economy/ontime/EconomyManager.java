package br.com.finalcraft.evernifecore.economy.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.integration.VaultIntegration;
import org.bukkit.Bukkit;

public class EconomyManager {

    private static IEconomyProvider ECONOMY_PROVIDER = new IEconomyProvider() {
        @Override
        public double ecoGet(PlayerData playerData) {
            return VaultIntegration.ecoGet(Bukkit.getOfflinePlayer(playerData.getUniqueId()));
        }

        @Override
        public void ecoGive(PlayerData playerData, double value) {
            VaultIntegration.ecoGive(Bukkit.getOfflinePlayer(playerData.getUniqueId()), value);
        }

        @Override
        public boolean ecoTake(PlayerData playerData, double value) {
            return VaultIntegration.ecoTake(Bukkit.getOfflinePlayer(playerData.getUniqueId()), value);
        }

        @Override
        public void ecoSet(PlayerData playerData, double value) {
            VaultIntegration.ecoSet(Bukkit.getOfflinePlayer(playerData.getUniqueId()), value);
        }
    };

    public static void setEconomyProvider(IEconomyProvider economyProvider) {
        ECONOMY_PROVIDER = economyProvider;
    }

    public static IEconomyProvider getProvider() {
        return ECONOMY_PROVIDER;
    }
}
