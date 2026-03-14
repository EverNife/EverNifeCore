package br.com.finalcraft.evernifecore.nms.util;

import br.com.finalcraft.evernifecore.nms.data.IMCMaterialRegistry;
import br.com.finalcraft.evernifecore.nms.data.IMcBlockWrapper;
import br.com.finalcraft.evernifecore.nms.data.IMcItemWrapper;
import br.com.finalcraft.evernifecore.nms.data.oredict.IMCOreRegistry;
import org.bukkit.World;
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

	public String getEntityRegistryName(Entity entity);

	public ItemStack getItemFromMinecraftIdentifier(String minecraftIdentifier);

	public String getLocalizedName(ItemStack itemStack);

	public org.bukkit.inventory.ItemStack asBukkitItemStack(Object mcItemStack);

	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack);

	public String serializeItemStack(org.bukkit.inventory.ItemStack itemStack);

	public World asBukkitWorld(Object minecraftWorld);

	public Object asMinecraftWorld(World bukkitWorld);

	public void autoRespawnOnDeath(Player player);

	public boolean isTool(ItemStack itemStack);

	public boolean isSword(ItemStack itemStack);

	public boolean isArmor(ItemStack itemStack);

	public boolean isHelmet(ItemStack itemStack);

	public boolean isChestplate(ItemStack itemStack);

	public boolean isLeggings(ItemStack itemStack);

	public boolean isBoots(ItemStack itemStack);

	public boolean isFakePlayer(Player player);

	public Entity asBukkitEntity(Object minecraftEntity);

	public Object asMinecraftEntity(Entity entity);

	public ItemStack validateItemStackHandle(ItemStack itemStack); //This method makes sure the item has a valid handle

	public default IMCMaterialRegistry<IMcBlockWrapper> getBlockRegistry(){
		throw new UnsupportedOperationException("Not implemented");
	}

	public default IMCMaterialRegistry<IMcItemWrapper> getItemRegistry(){
		throw new UnsupportedOperationException("Not implemented");
	}

	public default IMCOreRegistry getOreRegistry(){
		throw new UnsupportedOperationException("Not implemented");
	}
}