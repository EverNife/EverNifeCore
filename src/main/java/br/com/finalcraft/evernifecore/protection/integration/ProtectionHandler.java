package br.com.finalcraft.evernifecore.protection.integration;

import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * This is the interface used by EverNifeCore in order
 * to check permissions on other protection plugins.
 * */
public interface ProtectionHandler {

	/**
	 * Returns the name of the protection plugin.
	 * */
	String getName();

	/**
	 * Returns whether the player has build permission at the specified location.
	 * @param	player		the player to check for build permission
	 * @param	location	the location to check for build permission
	 * @return				whether the player has permission to build at the location
	 * */
	boolean canBuild(Player player, Location location);

	/**
	 * Returns whether the player has break permission at the specified location.
	 * @param	player		the player to check for break permission
	 * @param	location	the location to check for v permission
	 * @return				whether the player has permission to break at the location
	 * */
	boolean canBreak(Player player, Location location);
	
	/**
	 * Returns whether the player has the permission to interact with a block
	 * at the target location.
	 * @param	player		the player to check for interact permission
	 * @param	location	the location to check for interact permission
	 * @return				whether the player can with a block at the specified location
	 * */
	boolean canInteract(Player player, Location location);

	/**
	 * Returns whether the player has the permission to attack the specified entity.
	 * @param	damager		the player to check for attack permission
	 * @param	damaged		the entity attacked by the player
	 * @return				whether the player can attack the entity
	 * @see Entity
	 * */
	boolean canAttack(Player damager, Entity damaged);
	
	/**
	 * Returns whether the player has the permission to use an Area of Effect item at
	 * the specified location. The Area of Effect has the specified range in blocks.
	 * @param	player		the player to check for permission
	 * @param	location	the target location of the AoE action
	 * @param	range		the range of the AoE item
	 * @return				whether the player can use an AoE item at the target location
	 * */
	boolean canUseAoE(Player player, Location location, int range);

	/**
	 * Returns whether the player has the permission to break blocks at the specified
	 * cuboid selection at the given World.
	 * @param	player		    the player to check for permission
	 * @param	world	        the world to check the permission
	 * @param	cuboidSelection	the target cuboidRegion of the action
	 * @return				whether the player can break at the entire cuboid selection
	 * */
	boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection);

	/**
	 * Returns whether the player has the permission to break blocks at the specified
	 * cuboid selection at the given World.
	 * @param	player		    the player to check for permission
	 * @param	world	        the world to check the permission
	 * @param	cuboidSelection	the target cuboidRegion of the action
	 * @return				whether the player can break at the entire cuboid selection
	 * */
	boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection);
}
