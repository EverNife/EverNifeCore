package br.com.finalcraft.evernifecore.minecraft.api.events.base;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
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
        return playerData.getPDSection(pdSectionClass).join();
    }

    public Player getPlayer(){
        return playerData.getPlayer().getDelegate(Player.class);
    }

}
