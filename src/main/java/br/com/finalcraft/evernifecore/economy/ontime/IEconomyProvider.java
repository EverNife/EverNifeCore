package br.com.finalcraft.evernifecore.economy.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;

public interface IEconomyProvider {

    public double ecoGet(IPlayerData playerData);

    public void ecoGive(IPlayerData playerData, double value);

    public boolean ecoTake(IPlayerData playerData, double value);

    public void ecoSet(IPlayerData playerData, double value);

}
