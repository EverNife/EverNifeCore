package br.com.finalcraft.evernifecore.protection.handlers;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * This is the interface used by ForgeRestrictor in order
 * to check permissions on other protection plugins.
 * <p>
 * Protection plugin developers should implement this interface
 * and make a pull request to add it to net.kaikk.mc.fr.protectionplugins
 * package. A proper name for a class that implements this interface is
 * [ProtectionPluginName]Handler, e.g., GriefPreventionPlusHandler 
 * @see br.com.finalcraft.evernifecore.protection.handlers.protectionplugins.GriefPreventionPlusHandler
 * @see br.com.finalcraft.evernifecore.protection.handlers.protectionplugins.WorldGuardHandler
 *
 * This code comes from net.kaikk.mc.fr.protectionplugins
 * */
public interface ProtectionHandler {
	/**
	 * Returns the name of the protection plugin.
	 * */
	String getName();
	
	/** 
	 * Returns whether or not the player has build permission at
	 * the specified location.
	 * @param	player		the player to check for build permission
	 * @param	location	the location to check for build permission
	 * @return				whether or not the player has permission to build at the location
	 * */
	boolean canBuild(Player player, Location location);
	
	/** 
	 * Returns whether or not the player has the permission to
	 * access to levers, buttons, etc. at the specified target location.
	 * @param	player		the player to check for access permission
	 * @param	location	the location to check for access permission
	 * @return				whether or not the player has access permission at the target location
	 * */
	boolean canAccess(Player player, Location location);
	
	/** 
	 * Returns whether or not the player has the permission to use the
	 * item in hand at the specified target location.
	 * @param	player		the player to check for use permission
	 * @param	location	the location to check for use permission
	 * @return				whether or not the player has use permission at the target location
	 * */
	boolean canUse(Player player, Location location);
	
	/** 
	 * Returns whether or not the player has the permission to open the
	 * specified block container (chests, furnaces, etc.).
	 * The specified block implements InventoryHolder interface.
	 * @param	player		the player to check for open container permission
	 * @param	block		the block to check for open container permission
	 * @return				whether or not the player can open the container
	 * @see org.bukkit.inventory.InventoryHolder
	 * */
	boolean canOpenContainer(Player player, Block block);
	
	/** 
	 * Returns whether or not the player has the permission to
	 * interact with a block at the target location.
	 * @param	player		the player to check for interact permission
	 * @param	location	the location to check for interact permission
	 * @return				whether or not the player can with a block at the specified location
	 * */
	boolean canInteract(Player player, Location location);

	/** 
	 * Returns whether or not the player has the permission to attack the specified entity.
	 * @param	damager		the player to check for attack permission
	 * @param	damaged		the entity attacked by the player
	 * @return				whether or not the player can attack the entity
	 * @see org.bukkit.entity.Entity
	 * */
	boolean canAttack(Player damager, Entity damaged);
	
	/** 
	 * Returns whether or not the player has the permission to hit the target
	 * location with a projectile (e.g. arrow).
	 * @param	player		the player to check for permission
	 * @param	location	the target location being hit by a projectile shot by the player
	 * @return				whether or not the player can hit the target location with a projectile
	 * */
	boolean canProjectileHit(Player player, Location location);
	
	/** 
	 * Returns whether or not the player has the permission to use an Area of Effect item at
	 * the specified location. The Area of Effect has the specified range in blocks.
	 * @param	player		the player to check for permission
	 * @param	location	the target location of the AoE action
	 * @param	range		the range of the AoE item
	 * @return				whether or not the player can use an AoE item at the target location
	 * */
	boolean canUseAoE(Player player, Location location, int range);
}
