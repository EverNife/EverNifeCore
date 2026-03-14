package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.integration.VaultIntegration;

public class EconomyManager {

    private static IEconomyProvider ECONOMY_PROVIDER = new IEconomyProvider() {
        @Override
        public double ecoGet(IPlayerData playerData) {
            return VaultIntegration.ecoGet(playerData.getUniqueId());
        }

        @Override
        public void ecoGive(IPlayerData playerData, double value) {
            VaultIntegration.ecoGive(playerData.getUniqueId(), value);
        }

        @Override
        public boolean ecoTake(IPlayerData playerData, double value) {
            return VaultIntegration.ecoTake(playerData.getUniqueId(), value);
        }

        @Override
        public void ecoSet(IPlayerData playerData, double value) {
            VaultIntegration.ecoSet(playerData.getUniqueId(), value);
        }
    };

    public static void setEconomyProvider(IEconomyProvider economyProvider) {
        ECONOMY_PROVIDER = economyProvider;
    }

    public static IEconomyProvider getProvider() {
        return ECONOMY_PROVIDER;
    }
}
