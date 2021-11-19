package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class PDSection {

    private final PlayerData playerData;

    public PDSection(PlayerData playerData) {
        this.playerData = playerData;
    }

    public boolean recentChanged = false;
    public void setRecentChanged(){
        this.playerData.setRecentChanged();
        if (this.recentChanged == false)
            this.recentChanged = true;
    }

    //Save Single PlayerData into YML
    public boolean forceSavePlayerData(){
        setRecentChanged();
        return this.playerData.forceSavePlayerData();
    }

    public abstract void savePDSection();

    public PlayerData getPlayerData() {
        return playerData;
    }

    public String getPlayerName() {
        return playerData.getPlayerName();
    }

    public UUID getUniqueId() {
        return playerData.getUniqueId();
    }

    public boolean isPlayerOnline(){
        return playerData.isPlayerOnline();
    }

    public Player getPlayer(){
        return playerData.getPlayer();
    }

    public Config getConfig() {
        return playerData.getConfig();
    }

    public long getLastSeen(){
        return playerData.getLastSeen();
    }

    public PlayerCooldown getCooldown(String identifier){
        return playerData.getCooldown(identifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return playerData.hashCode();
    }
}
