package br.com.finalcraft.evernifecore.nms.util.v1_16_R3;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.INMSUtils;
import br.com.finalcraft.evernifecore.version.ServerType;
import net.minecraft.server.v1_16_R3.*;
import org.apache.commons.lang3.Validate;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class NMSUtils_v1_16_R3 implements INMSUtils {

	public static NMSUtils_v1_16_R3 instance;

	private Class fakePlayerClass = null; 	// net.minecraftforge.common.util.FakePlayer
	private Field handle_field = null; 		// CraftItemStack.handle
	private Field tag_field = null; 		// ItemStack.tag

	public NMSUtils_v1_16_R3() {
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
				tag_field = ItemStack.class.getDeclaredField("tag");
				tag_field.setAccessible(true);
			}
		}catch (Exception e){
			throw new RuntimeException("Failed to check HandleField from CraftItemStack");
		}
	}

	@Override
	public String getLocalizedName(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
		String localizedName = nmsItem.getItem().h(nmsItem).getString();
		EnumItemRarity itemRarity = nmsItem.v();
		String prefixColor = itemRarity == EnumItemRarity.COMMON ? "" : itemRarity.e.toString();
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
		nmsItem.save(nbtTagCompound);
		return nbtTagCompound.toString();
	}

	@Override
	public void autoRespawnOnDeath(Player player){
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
			return armor.b() == EnumItemSlot.HEAD;
		}
		return false;
	}

	@Override
	public boolean isChestplate(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.b() == EnumItemSlot.CHEST;
		}
		return false;
	}

	@Override
	public boolean isLeggings(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.b() == EnumItemSlot.LEGS;
		}
		return false;
	}

	@Override
	public boolean isBoots(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.b() == EnumItemSlot.FEET;
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
		return ((net.minecraft.server.v1_16_R3.Entity) minecraftEntity).getBukkitEntity();
	}

	@Override
	public Object asMinecraftEntity(Entity entity) {
		return ((CraftEntity) entity).getHandle();
	}

	@Override
	public String getItemRegistryName(org.bukkit.inventory.ItemStack item) {
		ItemStack itemStack = getHandle(item);
		MinecraftKey minecraftKey = IRegistry.ITEM.getKey(itemStack.getItem());
		return minecraftKey.toString();
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
			Item item = IRegistry.ITEM.get(new MinecraftKey(args[0]));
			if (item == Items.AIR){
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
				NBTTagCompound nbtTagCompound = MojangsonParser.parse(stringBuilder.toString());
				itemStack.setTag(nbtTagCompound);
			}
			if (meta != 0){
				itemStack.setDamage(meta);
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