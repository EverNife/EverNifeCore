package br.com.finalcraft.evernifecore.api.events.base;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

public class ECPlayerDataEvent extends ECBaseEvent {

    public final PlayerData playerData;

    public ECPlayerDataEvent(PlayerData playerData) {
        this.playerData = playerData;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

}
