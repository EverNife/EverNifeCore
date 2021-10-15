/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package br.com.finalcraft.evernifecore.nms.util;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public interface INMSUtils {

	public boolean hasInventory(Block b);

	public boolean hasInventory(Entity e);

	public boolean isInventoryOpen(Player p);

	public String getOpenInventoryName(Player p);

	public void updateSlot(Player p, int slot, ItemStack item);

	public String getItemRegistryName(ItemStack item);

	public ItemStack getItemFromMinecraftIdentifier(String minecraftIdentifier);

	public ArrayList<ItemStack> getTopInvetnoryItems(Player p);

	public String toBaseBinary(ItemStack itemStack);

	public ItemStack fromBaseBinary(String baseBinary64);

	public String getNBTtoString(ItemStack itemStack);

	public void applyNBTFromString(ItemStack itemStack, String nbtJson);

	public void clearNBT(ItemStack itemStack);

	public String getLocalizedName(ItemStack itemStack);

	public org.bukkit.inventory.ItemStack asItemStack(Object mcItemStack);

	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack);

	public void autoRespawnOnDeath(Player player);

	public boolean hasNBTTagCompound(ItemStack itemStack);

	public void setNBTString(ItemStack itemStack, String key, String value);

	public String getNBTString(ItemStack itemStack, String key);

	public boolean isArmor(ItemStack itemStack);

	public boolean isHelmet(ItemStack itemStack);

	public boolean isChestplate(ItemStack itemStack);

	public boolean isLeggings(ItemStack itemStack);

	public boolean isBoots(ItemStack itemStack);

	public boolean isFakePlayer(Player player);

	public Object asMinecraftEntity(Entity entity);

	public ItemStack validateItemStackHandle(ItemStack itemStack);
}