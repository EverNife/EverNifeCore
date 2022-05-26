package br.com.finalcraft.evernifecore.nms.util;

import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import org.apache.commons.lang.NotImplementedException;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

//Todo Remake this entire NMS System
//  - Separate per modules
//  - Remove unnecessary functions
//  - Create a default interface for non "not implemented"
//  - Add simple way for "force integrate"
public interface INMSUtils {

	@Deprecated
	public default boolean hasInventory(Block b){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default boolean hasInventory(Entity e){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default boolean isInventoryOpen(Player p){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default String getOpenInventoryName(Player p){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default void updateSlot(Player p, int slot, ItemStack item){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	public String getItemRegistryName(ItemStack item);

	public ItemStack getItemFromMinecraftIdentifier(String minecraftIdentifier);

	@Deprecated
	public default ArrayList<ItemStack> getTopInvetnoryItems(Player p){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default String toBaseBinary(ItemStack itemStack){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default ItemStack fromBaseBinary(String baseBinary64){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default String getNBTtoString(ItemStack itemStack){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default void applyNBTFromString(ItemStack itemStack, String nbtJson){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default void clearNBT(ItemStack itemStack){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	public String getLocalizedName(ItemStack itemStack);

	public org.bukkit.inventory.ItemStack asItemStack(Object mcItemStack);

	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack);

	public void autoRespawnOnDeath(Player player);

	public default boolean hasNBTTagCompound(ItemStack itemStack){
		return FCNBTUtil.getFrom(itemStack).isEmpty();
	}

	@Deprecated
	public default void setNBTString(ItemStack itemStack, String key, String value){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	@Deprecated
	public default String getNBTString(ItemStack itemStack, String key){
		throw new NotImplementedException("This feature will be removed from the API, don't use it!");
	}

	public boolean isTool(ItemStack itemStack);

	public boolean isSword(ItemStack itemStack);

	public boolean isArmor(ItemStack itemStack);

	public boolean isHelmet(ItemStack itemStack);

	public boolean isChestplate(ItemStack itemStack);

	public boolean isLeggings(ItemStack itemStack);

	public boolean isBoots(ItemStack itemStack);

	public boolean isFakePlayer(Player player);

	public Object asMinecraftEntity(Entity entity);

	public ItemStack validateItemStackHandle(ItemStack itemStack);
}