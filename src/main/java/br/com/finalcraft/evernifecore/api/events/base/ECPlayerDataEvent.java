package br.com.finalcraft.evernifecore.api.events.base;

import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.entity.Player;

public class ECPlayerDataEvent extends ECBaseEvent {

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

    public Player getPlayer(){
        return playerData.getPlayer();
    }

}
