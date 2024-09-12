package br.com.finalcraft.evernifecore.nms.util.v1_20_R1;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.INMSUtils;
import br.com.finalcraft.evernifecore.version.ServerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.MojangsonParser;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayInClientCommand;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.item.*;
import org.apache.commons.lang3.Validate;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class NMSUtils_v1_20_R1 implements INMSUtils {

	public static NMSUtils_v1_20_R1 instance;

	private Class fakePlayerClass = null; 	// net.minecraftforge.common.util.FakePlayer
	private Field handle_field = null; 		// CraftItemStack.handle
	private Field tag_field = null; 		// ItemStack.tag

	public NMSUtils_v1_20_R1() {
		instance = this;
		try {
			if (ServerType.isModdedServer()){
				fakePlayerClass = Class.forName("net.minecraftforge.common.util.FakePlayer");
			}
		}catch (Exception e){
			EverNifeCore.info("Failed to find FakePlayer Forge's class... We are probably not on a forge server :D");
		}

		try {
			if (handle_field == null){
				handle_field = CraftItemStack.class.getDeclaredField("handle");
				handle_field.setAccessible(true);
			}
		}catch (Exception e){
			throw new RuntimeException("Failed to check HandleField from CraftItemStack");
		}

		try {
			if (tag_field == null){
				tag_field = ItemStack.class.getDeclaredField("v");
				tag_field.setAccessible(true);
			}
		}catch (Exception e){
			throw new RuntimeException("Failed to check NBTTagCompoundField from MCItemStack");
		}
	}

	@Override
	public String getLocalizedName(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
		String localizedName = nmsItem.d().h(nmsItem).map(tooltipComponent -> tooltipComponent.toString()).orElse("null");
		EnumItemRarity itemRarity = nmsItem.C();
		String prefixColor = itemRarity == EnumItemRarity.a ? "" : itemRarity.e.toString();
		return prefixColor + localizedName;
	}

	@Override
	public org.bukkit.inventory.ItemStack asBukkitItemStack(Object mcItemStack){
		return CraftItemStack.asBukkitCopy((ItemStack) mcItemStack);
	}

	@Override
	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack) {
		return CraftItemStack.asNMSCopy(itemStack);
	}

	@Override
	public String serializeItemStack(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
		NBTTagCompound nbtTagCompound = new NBTTagCompound();
		nmsItem.b(nbtTagCompound);
		return nbtTagCompound.toString();
	}

	@Override
	public void autoRespawnOnDeath(Player player){
		CraftPlayer craftPlayer = (CraftPlayer) player;
		PacketPlayInClientCommand playInClientCommand = new PacketPlayInClientCommand(PacketPlayInClientCommand.EnumClientCommand.a);
		craftPlayer.getHandle().c.a(playInClientCommand);
	}

	@Override
	public boolean isTool(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		return mcItemStack.d() instanceof ItemTool;
	}

	@Override
	public boolean isSword(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		return mcItemStack.d() instanceof ItemSword;
	}

	@Override
	public boolean isArmor(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		return mcItemStack.d() instanceof ItemArmor;
	}

	@Override
	public boolean isHelmet(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.d() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.d();
			return armor.g() == EnumItemSlot.f;
		}
		return false;
	}

	@Override
	public boolean isChestplate(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.d() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.d();
			return armor.g() == EnumItemSlot.e;
		}
		return false;
	}

	@Override
	public boolean isLeggings(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.d() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.d();
			return armor.g() == EnumItemSlot.d;
		}
		return false;
	}

	@Override
	public boolean isBoots(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.d() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.d();
			return armor.g() == EnumItemSlot.c;
		}
		return false;
	}

	@Override
	public boolean isFakePlayer(Player player) {
		if (fakePlayerClass != null){
			CraftPlayer craftPlayer = (CraftPlayer) player;
			EntityPlayer entityPlayer = craftPlayer.getHandle();
			return fakePlayerClass.isInstance(entityPlayer);
		}
		return false;
	}

	@Override
	public Entity asBukkitEntity(Object minecraftEntity) {
		return ((net.minecraft.world.entity.Entity) minecraftEntity).getBukkitEntity();
	}

	@Override
	public Object asMinecraftEntity(Entity entity) {
		return ((CraftEntity) entity).getHandle();
	}

	@Override
	public String getItemRegistryName(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		Item item = mcItemStack.d();
		MinecraftKey minecraftKey = BuiltInRegistries.i.b(item);
		return minecraftKey.toString();
	}

	@Override
	public String getEntityRegistryName(Entity entity) {
		CraftEntity craftEntity = (CraftEntity) entity;
		return craftEntity.getHandle().br();
	}

	@Override
	public org.bukkit.inventory.ItemStack validateItemStackHandle(org.bukkit.inventory.ItemStack itemStack) {
		if ( !(itemStack instanceof CraftItemStack) ){ //A fix for Dummy ItemStacks
			itemStack = CraftItemStack.asCraftMirror(CraftItemStack.asNMSCopy(itemStack));
		}
		if (getHandle(itemStack) == null){
            Item item = CraftMagicNumbers.getItem(itemStack.getType(), itemStack.getDurability());
            ItemStack handle = new ItemStack(item);
            setHandle(itemStack, handle);
		}
		return itemStack;
	}

	@Override
	public org.bukkit.inventory.ItemStack getItemFromMinecraftIdentifier(String minecraftIdentifier) {
		try {
			String[] args = minecraftIdentifier.split(" ");
			int count = args.length >= 2 ? Integer.parseInt(args[1]) : 1;
			int meta = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
			Item item = BuiltInRegistries.i.a(new MinecraftKey(args[0]));
			if (item instanceof ItemAir){
				throw new RuntimeException("No Registry found for: \"" + args[0] + "\" in [" + minecraftIdentifier + "]");
			}
			ItemStack itemStack = new ItemStack(item, count);
			if (args.length >= 4) {
				StringBuilder stringBuilder = new StringBuilder();
				for (int i = 3; i < args.length; i++) {
					if (i > 3) {
						stringBuilder.append(" ");
					}
					stringBuilder.append(args[i]);
				}
				NBTTagCompound nbtTagCompound = MojangsonParser.a(stringBuilder.toString());
				itemStack.b(nbtTagCompound);
			}
			if (meta != 0){
				itemStack.b(meta);
			}
			return CraftItemStack.asBukkitCopy(itemStack);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	private ItemStack getHandle(org.bukkit.inventory.ItemStack itemStack){
		Validate.notNull(itemStack,"itemStack can not be null!");
		try {
			CraftItemStack craftItemStack = (CraftItemStack) itemStack;
			ItemStack mcStack = (ItemStack) handle_field.get(craftItemStack);
			return mcStack;
		}catch (Exception e){
			Class c = itemStack.getClass();
			EverNifeCore.warning("Failed to get ItemStack Handle for:" +
					"\n" +
					"\nPackage: " + c.getPackage()+"" +
					"\nClass: " + c.getSimpleName()+"" +
					"\nFull Identifier: " + c.getName());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private void setHandle(org.bukkit.inventory.ItemStack mcStack, ItemStack handle){
		Validate.notNull(mcStack,"mcStack can not be null!");
		Validate.notNull(handle,"handle can not be null!");
		try {
			CraftItemStack craftItemStack = (CraftItemStack) mcStack;
			handle_field.set(craftItemStack, handle);
		}catch (Exception e){
			Class c = mcStack.getClass();
			EverNifeCore.warning("ItemStack Class:\n\n Package: "+c.getPackage()+"\nClass: "+c.getSimpleName()+"\nFull Identifier: "+c.getName());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}