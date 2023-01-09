package br.com.finalcraft.evernifecore.nms.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


//Todo Remake this entire NMS System
//  - Separate per modules
//  - Remove unnecessary functions
//  - Create a default interface for non "not implemented"
//  - Add simple way for "force integrate"
public interface INMSUtils {

	public String getItemRegistryName(ItemStack item);

	public ItemStack getItemFromMinecraftIdentifier(String minecraftIdentifier);

	public String getLocalizedName(ItemStack itemStack);

	public org.bukkit.inventory.ItemStack asItemStack(Object mcItemStack);

	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack);

	public void autoRespawnOnDeath(Player player);

	public boolean isTool(ItemStack itemStack);

	public boolean isSword(ItemStack itemStack);

	public boolean isArmor(ItemStack itemStack);

	public boolean isHelmet(ItemStack itemStack);

	public boolean isChestplate(ItemStack itemStack);

	public boolean isLeggings(ItemStack itemStack);

	public boolean isBoots(ItemStack itemStack);

	public boolean isFakePlayer(Player player);

	public Object asMinecraftEntity(Entity entity);

	public ItemStack validateItemStackHandle(ItemStack itemStack); //This method makes sure the item has a valid handle
}