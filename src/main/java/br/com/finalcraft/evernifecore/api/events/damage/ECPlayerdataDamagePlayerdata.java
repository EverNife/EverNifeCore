package br.com.finalcraft.evernifecore.api.events.damage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.listeners.PlayerDamageByEntityListener;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This event is fired when a player's property (a player, a projectile or a pet)
 * causes damage to another player's property (a player or a pet)!
 *
 * @author EverNife
 */
public class ECPlayerdataDamagePlayerdata extends Event implements Cancellable {

    protected static boolean hasBeenRegistered = false;

    private static final HandlerList handlers = new HandlerList(){
        @Override
        public synchronized void register(RegisteredListener listener) {
            super.register(listener);
            if (hasBeenRegistered == false){
                hasBeenRegistered = true;
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        ECListener.register(EverNifeCore.instance, PlayerDamageByEntityListener.class);
                    }
                }.runTaskLater(EverNifeCore.instance, 1);
            }
        }
    };

    protected final PlayerData attackerData;
    protected final PlayerData victimData;
    protected final EntityDamageByEntityEvent entityDamageByEntityEvent;

    public ECPlayerdataDamagePlayerdata(PlayerData attackerData, PlayerData victimData, EntityDamageByEntityEvent entityDamageByEntityEvent) {
        this.attackerData = attackerData;
        this.victimData = victimData;
        this.entityDamageByEntityEvent = entityDamageByEntityEvent;
    }

    public PlayerData getAttackerData() {
        return attackerData;
    }

    public PlayerData getVictimData() {
        return victimData;
    }

    public boolean isProjectileDamage(){
        return entityDamageByEntityEvent.getDamager() instanceof Projectile;
    }

    public List<Tameable> getPetsInvolved(){
        if (this instanceof ECPlayerDamagedByPet){
            return Arrays.asList(((ECPlayerDamagedByPet) this).getAttacker());
        }
        if (this instanceof ECPetDamagedByPlayer){
            return Arrays.asList(((ECPetDamagedByPlayer) this).getVictim());
        }
        if (this instanceof ECPetDamagedByPet){
            return Arrays.asList(((ECPetDamagedByPet) this).getAttacker(), ((ECPetDamagedByPet) this).getVictim());
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * Get the Original EntityDamageByEntityEvent
     *
     * @return The {@link EntityDamageByEntityEvent}
     * @author EverNife
     */
    public EntityDamageByEntityEvent getOriginalEvent() {
        return entityDamageByEntityEvent;
    }

    @Override
    public boolean isCancelled() {
        return entityDamageByEntityEvent.isCancelled();
    }

    @Override
    public void setCancelled(boolean cancel) {
        entityDamageByEntityEvent.setCancelled(cancel);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
