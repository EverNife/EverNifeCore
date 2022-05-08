package br.com.finalcraft.evernifecore.nbt;

import de.tr7zw.changeme.nbtapi.NBTEntity;
import org.bukkit.entity.Player;

public class NBTPlayer extends NBTEntity{

    /**
     * @param player Any Online Player
     */
    public NBTPlayer(Player player) {
        super(player);
    }

    public float getHealth(){
        return this.getFloat("Health");
    }

    public void setHealth(float health){
        this.setFloat("Health", health);
    }
}
