package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.api.events.damage.*;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Note: This listener is registered only once
 * and on Demand by the Events:
 *
 * @see ECPlayerdataDamagePlayerdata
 * @see ECPlayerDamagedByPlayer
 * @see ECPlayerDamagedByPet
 * @see ECPetDamagedByPet
 * @see ECPetDamagedByPlayer
 */
public class PlayerDamageByEntityListener implements ECListener {

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
                ? PlayerController.getPlayerData((Player) attackerEntity)
                : null;
        PlayerData victimData = victimEntity instanceof Player
                ? PlayerController.getPlayerData((Player) victimEntity)
                : null;

        //Maybe the damage cause is an Arrow or another Projectile
        if (attackerData == null && attackerEntity instanceof Projectile){
            Projectile projectile = (Projectile) attackerEntity;
            if (projectile.getShooter() instanceof Player){
                attackerEntity = (Player) projectile.getShooter();
                attackerData = PlayerController.getPlayerData((Player) attackerEntity);
            }

            //Maybe the projectile shooter is a Pet itself
            if (projectile.getShooter() instanceof Tameable){
                tamableAttacker = (Tameable) projectile.getShooter();
                if (tamableAttacker.isTamed()){
                    attackerData = PlayerController.getPlayerData(tamableAttacker.getOwner().getUniqueId());
                }
            }
        }

        //Maybe the attacker is a Wolf or a Pet
        if (attackerData == null && attackerEntity instanceof Tameable){
            tamableAttacker = (Tameable) attackerEntity;
            if (tamableAttacker.isTamed()){
                attackerData = PlayerController.getPlayerData(tamableAttacker.getOwner().getUniqueId());
            }
        }

        //Maybe even the victim is a Wolf or a Pet
        if (victimData == null && victimEntity instanceof Tameable){
            tamableVictim = (Tameable) victimEntity;
            if (tamableVictim.isTamed()){
                victimData = PlayerController.getPlayerData(tamableVictim.getOwner().getUniqueId());
            }
        }

        if (attackerData != null && victimData != null){

            //Player damages a Player
            if (tamableAttacker == null && tamableVictim == null){
                Bukkit.getPluginManager().callEvent(
                        new ECPlayerDamagedByPlayer(
                                attackerData,
                                victimData,
                                event
                        )
                );
                return;
            }

            //Pet damages a Player
            if (tamableAttacker != null && tamableVictim == null){
                Bukkit.getPluginManager().callEvent(
                        new ECPlayerDamagedByPet(
                                attackerData,
                                tamableAttacker,
                                victimData,
                                event
                        )
                );
                return;
            }

            //Pet damages a Pet
            if (tamableAttacker != null && tamableVictim != null){
                Bukkit.getPluginManager().callEvent(
                        new ECPetDamagedByPet(
                                attackerData,
                                tamableAttacker,
                                victimData,
                                tamableVictim,
                                event
                        )
                );
                return;
            }

            //Player damages a Pet
            if (tamableAttacker == null && tamableVictim != null){
                Bukkit.getPluginManager().callEvent(
                        new ECPetDamagedByPlayer(
                                attackerData,
                                victimData,
                                tamableVictim,
                                event
                        )
                );
                return;
            }
        }
    }

}
