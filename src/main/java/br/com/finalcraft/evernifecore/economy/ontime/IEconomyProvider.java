package br.com.finalcraft.evernifecore.economy.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

public interface IEconomyProvider {

    public double ecoGet(PlayerData playerData);

    public void ecoGive(PlayerData playerData, double value);

    public boolean ecoTake(PlayerData playerData, double value);

    public void ecoSet(PlayerData playerData, double value);

}
