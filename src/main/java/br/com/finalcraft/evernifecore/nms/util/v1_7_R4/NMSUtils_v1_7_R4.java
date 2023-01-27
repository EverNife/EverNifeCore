package br.com.finalcraft.evernifecore.nms.util.v1_7_R4;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.INMSUtils;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.reflection.FieldAccessor;
import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import net.minecraft.server.v1_7_R4.*;
import org.apache.commons.lang3.Validate;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_7_R4.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class NMSUtils_v1_7_R4 implements INMSUtils {

	public static NMSUtils_v1_7_R4 instance;

	private final MethodInvoker<NBTTagCompound> crucible_JsonToNBT_getTagFromJson = FCReflectionUtil.getMethod(			//Crucible_JsonToNBT.getTagFromJson()
			"io.github.crucible.nbt.Crucible_JsonToNBT","getTagFromJson", String.class
	);

	private final FieldAccessor<ItemStack> handle_field = FCReflectionUtil.getField( 										// CraftItemStack.handle
			CraftItemStack.class,"handle", ItemStack.class
	);

	private final FieldAccessor<net.minecraft.server.v1_7_R4.Entity> entity_field = FCReflectionUtil.getField(			// CraftEntity.entity
			CraftEntity.class,"entity", net.minecraft.server.v1_7_R4.Entity.class
	);
	private Class fakePlayerClass = null; 																				// net.minecraftforge.common.util.FakePlayer

	public NMSUtils_v1_7_R4() {
		instance = this;
		try {
			fakePlayerClass = Class.forName("net.minecraftforge.common.util.FakePlayer");
		}catch (Exception e){
			EverNifeCore.info("Failed to find FakePlayer Forge's class...");
			e.printStackTrace();
		}
		Validate.notNull(handle_field,"CraftItemStack.class 'handle' field cannot be null!");
		Validate.notNull(entity_field,"CraftEntity.class 'entity' field cannot be null!");
	}

	private EntityHuman getNMSPlayer(org.bukkit.entity.Player p) {
		CraftPlayer cplayer = (CraftPlayer) p;
		EntityHuman nmshuman = cplayer.getHandle();
		return nmshuman;
	}

	@Override
	public String getLocalizedName(org.bukkit.inventory.ItemStack itemStack) {
		net.minecraft.server.v1_7_R4.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
		return nmsItem.getName();
	}

	@Override
	public org.bukkit.inventory.ItemStack asItemStack(Object mcItemStack){
		return CraftItemStack.asCraftMirror((net.minecraft.server.v1_7_R4.ItemStack) mcItemStack);
	}

	@Override
	public Object asMinecraftItemStack(org.bukkit.inventory.ItemStack itemStack) {
		return CraftItemStack.asNMSCopy(itemStack);
	}

	@Override
	public void autoRespawnOnDeath(org.bukkit.entity.Player player){
		CraftPlayer craftPlayer = (CraftPlayer) player;
		PacketPlayInClientCommand playInClientCommand = new PacketPlayInClientCommand(EnumClientCommand.PERFORM_RESPAWN);
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
			return armor.b == 0;
		}
		return false;
	}

	@Override
	public boolean isChestplate(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.b == 1;
		}
		return false;
	}

	@Override
	public boolean isLeggings(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.b == 2;
		}
		return false;
	}

	@Override
	public boolean isBoots(org.bukkit.inventory.ItemStack itemStack) {
		ItemStack mcItemStack = getHandle(itemStack);
		if (mcItemStack.getItem() instanceof ItemArmor){
			ItemArmor armor = (ItemArmor) mcItemStack.getItem();
			return armor.b == 3;
		}
		return false;
	}

	@Override
	public boolean isFakePlayer(Player player) {
		if (fakePlayerClass != null){
			net.minecraft.server.v1_7_R4.Entity mcEntity = (net.minecraft.server.v1_7_R4.Entity) asMinecraftEntity(player);
			return fakePlayerClass.isInstance(mcEntity);
		}
		return false;
	}

	@Override
	public Object asMinecraftEntity(Entity entity) {
		try {
			CraftEntity craftEntity = (CraftEntity) entity;
			net.minecraft.server.v1_7_R4.Entity mcEntity = entity_field.get(craftEntity);
			return mcEntity;
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getItemRegistryName(org.bukkit.inventory.ItemStack item) {
		ItemStack itemStack = getHandle(item);
		return Item.REGISTRY.c(itemStack.getItem());
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
			Item item = (Item) Item.REGISTRY.get(args[0]);
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
				NBTTagCompound nbtTagCompound = crucible_JsonToNBT_getTagFromJson.invoke(null, stringBuilder.toString());
				itemStack.setTag(nbtTagCompound);
			}
			return CraftItemStack.asCraftMirror(itemStack);
		}catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	private ItemStack getHandle(org.bukkit.inventory.ItemStack itemStack){
		Validate.notNull(itemStack,"itemStack can not be null!");
		try {
			CraftItemStack craftItemStack = (CraftItemStack) itemStack;
			ItemStack mcStack = handle_field.get(craftItemStack);
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