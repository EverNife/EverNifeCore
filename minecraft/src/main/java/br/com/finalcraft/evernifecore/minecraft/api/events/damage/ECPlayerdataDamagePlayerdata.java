package br.com.finalcraft.evernifecore.minecraft.api.events.damage;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This event is fired when a player's property (a player, a projectile or a pet)
 * causes damage to another player's property (a player or a pet)!
 *
 * The four concrete events of this family share this one native handler list, so a listener on this
 * base hears them all. They are produced only while somebody listens: the core registers its
 * {@code EntityDamageByEntityEvent} listener on the first listener of any of them and drops it with
 * the last, whether that listener sits on the bus or on the server.
 *
 * @author EverNife
 */
public class ECPlayerdataDamagePlayerdata extends ECEvent implements ECCancellable {

    public static HandlerList getHandlerList() {
        return (HandlerList) ECEvent.getHandlerListOf(ECPlayerdataDamagePlayerdata.class);
    }

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

}
