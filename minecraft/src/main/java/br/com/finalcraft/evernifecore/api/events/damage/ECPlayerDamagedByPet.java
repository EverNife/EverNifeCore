package br.com.finalcraft.evernifecore.api.events.damage;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * This event is fired when a pet causes damage to another player!
 * The damage can come from third-part sources like a Projectile.
 *
 * @author EverNife
 */
public class ECPlayerDamagedByPet extends ECPlayerdataDamagePlayerdata {

    private final Tameable tamableAttacker;

    public ECPlayerDamagedByPet(PlayerData attackerData, Tameable tamableAttacker, PlayerData victimData, EntityDamageByEntityEvent entityDamageByEntityEvent) {
        super(attackerData, victimData, entityDamageByEntityEvent);
        this.tamableAttacker = tamableAttacker;
    }

    public Tameable getAttacker() {
        return tamableAttacker;
    }

    public Player getVictim() {
        return (Player) entityDamageByEntityEvent.getEntity();
    }

}
