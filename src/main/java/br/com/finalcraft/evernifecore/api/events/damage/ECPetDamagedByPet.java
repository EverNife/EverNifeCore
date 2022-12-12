package br.com.finalcraft.evernifecore.api.events.damage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.listeners.PlayerCraftListener;
import br.com.finalcraft.evernifecore.listeners.PlayerDamageByEntityListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

/**
 * This event is fired when a pet causes damage to another pet
 * The damage can come from thid-part sources like a Projectile.
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
