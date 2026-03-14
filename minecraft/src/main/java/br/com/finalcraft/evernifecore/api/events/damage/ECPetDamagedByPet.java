package br.com.finalcraft.evernifecore.api.events.damage;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * This event is fired when a pet causes damage to another pet
 * The damage can come from third-part sources like a Projectile.
 *
 * @author EverNife
 */
public class ECPetDamagedByPet extends ECPlayerdataDamagePlayerdata {

    private final Tameable tamableAttacker;
    private final Tameable tamableVictim;

    public ECPetDamagedByPet(PlayerData attackerData, Tameable tamableAttacker, PlayerData victimData, Tameable tamableVictim, EntityDamageByEntityEvent entityDamageByEntityEvent) {
        super(attackerData, victimData, entityDamageByEntityEvent);
        this.tamableAttacker = tamableAttacker;
        this.tamableVictim = tamableVictim;
    }

    public Tameable getAttacker() {
        return tamableAttacker;
    }

    public Tameable getVictim() {
        return tamableVictim;
    }

}
