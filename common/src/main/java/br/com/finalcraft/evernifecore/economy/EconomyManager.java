package br.com.finalcraft.evernifecore.economy;

public class EconomyManager {

    private static IEconomyProvider ECONOMY_PROVIDER;

    public static void setEconomyProvider(IEconomyProvider economyProvider) {
        ECONOMY_PROVIDER = economyProvider;
    }

    public static IEconomyProvider getProvider() {
        return ECONOMY_PROVIDER;
    }
}
