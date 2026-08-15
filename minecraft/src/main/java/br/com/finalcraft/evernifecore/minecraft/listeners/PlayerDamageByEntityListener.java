package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPetDamagedByPet;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPetDamagedByPlayer;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPlayerDamagedByPet;
import br.com.finalcraft.evernifecore.minecraft.api.events.damage.ECPlayerDamagedByPlayer;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Produces the four events of the {@code ECPlayerdataDamagePlayerdata} family. Registered with the
 * server only while somebody listens to any of them - {@link ECListener#registerWhileListened}.
 */
public class PlayerDamageByEntityListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        //comes and goes with the listeners of what it produces; logging each turn would be noise
        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageEvent(EntityDamageByEntityEvent event) {
        if (event.isCancelled()){
            return;
        }

        Entity attackerEntity = event.getDamager();
        Tameable tamableAttacker = null;
        Entity victimEntity = event.getEntity();
        Tameable tamableVictim = null;

        PlayerData attackerData = attackerEntity instanceof Player
                ? PlayerController.getLoaded(attackerEntity.getUniqueId())
                : null;

        PlayerData victimData = victimEntity instanceof Player
                ? PlayerController.getLoaded(victimEntity.getUniqueId())
                : null;

        //Maybe the damage cause is an Arrow or another Projectile
        if (attackerData == null && attackerEntity instanceof Projectile){
            Projectile projectile = (Projectile) attackerEntity;
            if (projectile.getShooter() instanceof Player){
                attackerEntity = (Player) projectile.getShooter();
                attackerData = PlayerController.getLoaded(attackerEntity.getUniqueId());
            }

            //Maybe the projectile shooter is a Pet itself
            if (projectile.getShooter() instanceof Tameable){
                tamableAttacker = (Tameable) projectile.getShooter();
                if (tamableAttacker.isTamed()){
                    attackerData = PlayerController.getLoaded(tamableAttacker.getOwner().getUniqueId());
                }
            }
        }

        //Maybe the attacker is a Wolf or a Pet
        if (attackerData == null && attackerEntity instanceof Tameable){
            tamableAttacker = (Tameable) attackerEntity;
            if (tamableAttacker.isTamed()){
                attackerData = PlayerController.getLoaded(tamableAttacker.getOwner().getUniqueId());
            }
        }

        //Maybe even the victim is a Wolf or a Pet
        if (victimData == null && victimEntity instanceof Tameable){
            tamableVictim = (Tameable) victimEntity;
            if (tamableVictim.isTamed()){
                victimData = PlayerController.getLoaded(tamableVictim.getOwner().getUniqueId());
            }
        }

        if (attackerData == null || victimData == null){
            return;
        }

        //captured by the suppliers below, which only run for a listener
        final PlayerData attacker = attackerData;
        final PlayerData victim = victimData;
        final Tameable attackerPet = tamableAttacker;
        final Tameable victimPet = tamableVictim;
        ECEventBus bus = ECEventBus.global();

        //Player damages a Player
        if (attackerPet == null && victimPet == null){
            bus.postIfListened(ECPlayerDamagedByPlayer.class,
                    () -> new ECPlayerDamagedByPlayer(attacker, victim, event));
            return;
        }

        //Pet damages a Player
        if (attackerPet != null && victimPet == null){
            bus.postIfListened(ECPlayerDamagedByPet.class,
                    () -> new ECPlayerDamagedByPet(attacker, attackerPet, victim, event));
            return;
        }

        //Pet damages a Pet
        if (attackerPet != null && victimPet != null){
            bus.postIfListened(ECPetDamagedByPet.class,
                    () -> new ECPetDamagedByPet(attacker, attackerPet, victim, victimPet, event));
            return;
        }

        //Player damages a Pet
        bus.postIfListened(ECPetDamagedByPlayer.class,
                () -> new ECPetDamagedByPlayer(attacker, victim, victimPet, event));
    }

}
