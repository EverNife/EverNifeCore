package br.com.finalcraft.evernifecore.api.events.base;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

public class ECPlayerDataEvent implements IECBaseEvent {

    public final PlayerData playerData;

    public ECPlayerDataEvent(PlayerData playerData) {
        this.playerData = playerData;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public <T extends PDSection> T getPDSection(Class<T> pdSectionClass) {
        return playerData.getPDSection(pdSectionClass);
    }

    public FPlayer getPlayer(){
        return playerData.getPlayer();
    }

}
