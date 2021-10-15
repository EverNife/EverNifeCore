package br.com.finalcraft.evernifecore.protection;

import br.com.finalcraft.evernifecore.protection.handlers.ProtectionHandler;
import br.com.finalcraft.evernifecore.protection.handlers.ProtectionPlugins;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class ProtectionAll {

    /**
     * Returns whether or not the player has build permission at
     * the specified location.
     * @param	player		the player to check for build permission
     * @param	location	the location to check for build permission
     * @return				whether or not the player has permission to build at the location
     * */
    public static boolean canBuild(Player player, Location location){
        for (ProtectionHandler protection : ProtectionPlugins.getHandlers()) {
            if (!protection.canBuild(player,location)){
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether or not the player has the permission to hit the target
     * location with a projectile (e.g. arrow).
     * @param	player		the player to check for permission
     * @param	location	the target location being hit by a projectile shot by the player
     * @return				whether or not the player can hit the target location with a projectile
     * */
    public static boolean canProjectileHit(Player player, Location location){
        for (ProtectionHandler protection : ProtectionPlugins.getHandlers()) {
            if (!protection.canProjectileHit(player,location)){
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether or not the player has the permission to attack the specified entity.
     * @param	damager		the player to check for attack permission
     * @param	damaged		the entity attacked by the player
     * @return				whether or not the player can attack the entity
     * @see org.bukkit.entity.Entity
     * */
    public static boolean canAttack(Player damager, Entity damaged){
        for (ProtectionHandler protection : ProtectionPlugins.getHandlers()) {
            if (!protection.canAttack(damager,damaged)){
                return false;
            }
        }
        return true;
    }
}
