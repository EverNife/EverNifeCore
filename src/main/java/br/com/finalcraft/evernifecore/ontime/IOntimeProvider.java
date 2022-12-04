package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;

public interface IOntimeProvider {

    public long getOntime(IPlayerData playerData);

}
