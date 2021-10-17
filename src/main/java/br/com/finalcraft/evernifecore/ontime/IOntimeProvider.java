package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

public interface IOntimeProvider {

    public long getOntime(PlayerData playerData);

}
