package br.com.finalcraft.evernifecore.nms.util.v1_12_R1;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.INMSUtils;
import br.com.finalcraft.evernifecore.version.ServerType;
import net.minecraft.server.v1_12_R1.*;
import org.apache.commons.lang3.Validate;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class NMSUtils_v1_12_R1 implements INMSUtils {

	public static NMSUtils_v1_12_R1 instance;

	private Class fakePlayerClass = null; 	// net.minecraftforge.common.util.FakePlayer
	private Field handle_field = null; 		// CraftItemStack.handle
	private Field entity_field = null; 		// CraftEntity.entity
	private Field tag_field = null; 		// ItemStack.tag

	public NMSUtils_v1_12_R1() {
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
			if (entity_field == null){
				entity_field = CraftEntity.class.getDeclaredField("entity");
				entity_field.setAccessible(true);
			}
		}catch (Exception e){
			throw new RuntimeException("Failed to check HandleField from CraftItemStack");
		}

		try {
			if (tag_field == null){
				tag_field = ItemStack.class.getDeclaredField("tag");
				tag_field.setAccessible(true);
			}
		}catch (Exception e){
			throw new RuntimeException("Failed to check HandleField from CraftItemStack");
		}
	}

	private Container getPlayerContainer(org.bukkit.entity.Player p) {
		return getNMSPlayer(p).activeContainer;
	}

	private EntityHuman getNMSPlayer(org.bukkit.entity.Player p) {
		CraftPlayer cplayer = (CraftPlayer) p;
		EntityHuman nmshuman = cplayer.getHandle();
		return nmshuman;
	}

	@Override
	public String getLocalizedName(org.bukkit.inventory.ItemStack itemStack) {
		net.minecraft.server.v1_12_R1.ItemStack nmsItem = org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asNMSCopy(itemStack);
		String localizedName = nmsItem.getItem().b(nmsItem);
		EnumItemRarity itemRarity = nmsItem.v();
		String prefixColor = itemRarity == EnumItemRarity.COMMON ? "" : itemRarity.e.toString();
		return prefixColor + localizedName;
	}

	@Override
	public org.bukkit.inventory.ItemStack asItemStack(Object mcItemStack){
		return org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asBukkitCopy((net.minecraft.server.v1_12_R1.ItemStack) mcItemStack);
	}

	@Override
	public String serializeItemStack(org.bukkit.inventory.ItemStack itemStack) {
		net.minecraft.server.v1_12_R1.ItemStack nmsItem = org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asNMSCopy(itemStack);
		NBTTagCompound compound = new NBTTagCompound();
		nmsItem.save(compound);
		return compound.toString();
	}

	@Override
	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack) {
		return org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack.asNMSCopy(itemStack);
	}

	@Override
	public void autoRespawnOnDeath(org.bukkit.entity.Player player){
		CraftPlayer craftPlayer = (CraftPlayer) player;
		PacketPlayInClientCommand playInClientCommand = new PacketPlayInClientCommand(PacketPlayInClientCommand.EnumClientCommand.PERFORM_RESPAWN);
		craftPlayer.getHandle().playerConnection.a(playInClientCommand);
	}

	@Override
	public boolean isTool(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		return mcItemStack.getItem() instanceof ItemTool;
	}

	@Override
	public boolean isSword(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		return mcItemStack.getItem() instanceof ItemSword;
	}

	@Override
	public boolean isArmor(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		return mcItemStack.getItem() instanceof ItemArmor;
	}

	@Override
	public boolean isHelmet(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.c == EnumItemSlot.HEAD;
		}
		return false;
	}

	@Override
	public boolean isChestplate(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.c == EnumItemSlot.CHEST;
		}
		return false;
	}

	@Override
	public boolean isLeggings(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.c == EnumItemSlot.LEGS;
		}
		return false;
	}

	@Override
	public boolean isBoots(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.c == EnumItemSlot.FEET;
		}
		return false;
	}

	@Override
	public boolean isFakePlayer(Player player) {
		if (fakePlayerClass != null){
			net.minecraft.server.v1_12_R1.Entity mcEntity = (net.minecraft.server.v1_12_R1.Entity) asMinecraftEntity(player);
			return fakePlayerClass.isInstance(mcEntity);
		}
		return false;
	}

	@Override
	public Object asMinecraftEntity(Entity entity) {
		try {
			CraftEntity craftEntity = (CraftEntity) entity;
			net.minecraft.server.v1_12_R1.Entity mcEntity = (net.minecraft.server.v1_12_R1.Entity) entity_field.get(craftEntity);
			return mcEntity;
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getItemRegistryName(org.bukkit.inventory.ItemStack item) {
		ItemStack itemStack = getHandle(item);
		MinecraftKey minecraftKey = Item.REGISTRY.b(itemStack.getItem());
		return minecraftKey.b() + ":" + minecraftKey.getKey();
	}

	@Override
	public org.bukkit.inventory.ItemStack validateItemStackHandle(org.bukkit.inventory.ItemStack itemStack) {
		if ( !(itemStack instanceof CraftItemStack) ){ //A fix for Dummy ItemStacks
			itemStack = CraftItemStack.asCraftMirror(CraftItemStack.asNMSCopy(itemStack));
		}
		if (getHandle(itemStack) == null){
			ItemStack handle = new ItemStack(CraftMagicNumbers.getItem(itemStack.getType()), itemStack.getAmount(), itemStack.getDurability());
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
			Item item = Item.REGISTRY.get(new MinecraftKey(args[0]));
			if (item == null){
				throw new RuntimeException("No Registry found for: \"" + args[0] + "\" in [" + minecraftIdentifier + "]");
			}
			ItemStack itemStack = new ItemStack(item, count, meta);
			if (args.length >= 4) {
				StringBuilder stringBuilder = new StringBuilder();
				for (int i = 3; i < args.length; i++) {
					if (i > 3) {
						stringBuilder.append(" ");
					}
					stringBuilder.append(args[i]);
				}
				NBTTagCompound nbtTagCompound = MojangsonParser.parse(stringBuilder.toString());
				itemStack.setTag(nbtTagCompound);
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