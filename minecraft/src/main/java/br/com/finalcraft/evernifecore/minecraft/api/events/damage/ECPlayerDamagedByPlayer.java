package br.com.finalcraft.evernifecore.minecraft.api.events.damage;

import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * This event is fired when a player causes damage to another player!
 * The damage can come from third-part sources like a Projectile.
 *
 * @author EverNife
 */
public class ECPlayerDamagedByPlayer extends ECPlayerdataDamagePlayerdata {

    public ECPlayerDamagedByPlayer(PlayerData attackerData, PlayerData victimData, EntityDamageByEntityEvent entityDamageByEntityEvent) {
        super(attackerData, victimData, entityDamageByEntityEvent);
    }

    public Player getAttacker(){
        return (Player) entityDamageByEntityEvent.getDamager();
    }

    public Player getVictim(){
        return (Player) entityDamageByEntityEvent.getEntity();
    }

}
