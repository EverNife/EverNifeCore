package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class PDSection implements IPlayerData{

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

    @Override
    public PlayerData getPlayerData() {
        return playerData;
    }

    @Override
    public String getPlayerName() {
        return playerData.getPlayerName();
    }

    @Override
    public UUID getUniqueId() {
        return playerData.getUniqueId();
    }

    @Override
    public boolean isPlayerOnline(){
        return playerData.isPlayerOnline();
    }

    @Override
    public Player getPlayer(){
        return playerData.getPlayer();
    }

    @Override
    public Config getConfig() {
        return playerData.getConfig();
    }

    @Override
    public long getLastSeen(){
        return playerData.getLastSeen();
    }

    @Override
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

    @Override
    public <T extends PDSection> T getPDSection(Class<T> pdSectionClass){
        if (this.getClass() == pdSectionClass) return (T)this;
        return playerData.getPDSection(pdSectionClass);
    }
}
