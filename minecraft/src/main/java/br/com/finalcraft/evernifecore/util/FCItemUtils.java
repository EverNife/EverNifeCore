package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.version.MCVersion;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class FCItemUtils {

    public static @Nullable String getDisplayName(ItemStack itemStack){
        ItemMeta itemMeta = itemStack.hasItemMeta() ? itemStack.getItemMeta() : null;
        if (itemMeta == null || !itemMeta.hasDisplayName()){
            return null;
        }
        return itemMeta.getDisplayName();
    }

    public static @Nonnull String getLocalizedName(ItemStack itemStack){
        return NMSUtils.get().getLocalizedName(itemStack);
    }

    public static void setDisplayName(ItemStack itemStack, String displayName){
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(displayName);
        itemStack.setItemMeta(itemMeta);
    }

    public static @Nonnull List<String> getLore(ItemStack itemStack){
        ItemMeta itemMeta = itemStack.hasItemMeta() ? itemStack.getItemMeta() : null;
        if (itemMeta == null || !itemMeta.hasLore()){
            return new ArrayList<>();
        }
        return itemMeta.getLore();
    }

    public static void setLore(ItemStack itemStack, List<String> loreLines){
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setLore(loreLines);
        itemStack.setItemMeta(itemMeta);
    }

    public static String getBukkitIdentifier(ItemStack itemStack){
        int durability = itemStack.getDurability();
        return itemStack.getType().name() + (durability == 0 ? "" : ":" + durability);
    }

    public static ItemStack fromIdentifier(String minecraftOrBukkitIdentifier){
        String[] split = minecraftOrBukkitIdentifier.split(":", 2);
        if (split.length == 1){ //If no ':' is present, then it can only be a BukkitIdentifier
            return fromBukkitIdentifier(minecraftOrBukkitIdentifier);
        }

        Integer bukkitDurability = FCInputReader.parseInt(split[1], null);
        if (bukkitDurability != null){ //The second part of the Identifier is a number, so, its a bukkit identifier
            return fromBukkitIdentifier(minecraftOrBukkitIdentifier);
        }

        return fromMinecraftIdentifier(minecraftOrBukkitIdentifier);
    }

    public static ItemStack fromBukkitIdentifier(String bukkitIdentifier){
        int meta = 0;
        if (bukkitIdentifier.contains(":")){
            String[] split = bukkitIdentifier.split(":");
            bukkitIdentifier = split[0];
            meta = FCInputReader.parseInt(split[1], 0);
            if (meta < 0) {
                meta = 0;
            }
        }
        Material material = FCInputReader.parseMaterial(bukkitIdentifier);
        if (material == null){
            throw new IllegalArgumentException("The identifier '" + bukkitIdentifier + "' is not a valid Bukkit Material!");
        }
        ItemStack itemStack = new ItemStack(material);
        if (meta != 0) {
            itemStack.setDurability((short) meta);
        }
        return itemStack;
    }

    public static ItemStack fromMinecraftIdentifier(String minecraftIdentifier){
        return NMSUtils.get().getItemFromMinecraftIdentifier(minecraftIdentifier);
    }

    public static String getMinecraftIdentifier(ItemStack itemStack){
        return getMinecraftIdentifier(itemStack, true);
    }

    public static String getMinecraftIdentifier(ItemStack itemStack, boolean withNbt){
        String nbt = null;
        itemStack = NMSUtils.get().validateItemStackHandle(itemStack);
        if (withNbt){
            NBTContainer nbtContainer = FCNBTUtil.getFrom(
                    FCNBTUtil.getFrom(itemStack).toString().toString()
            );
            if (MCVersion.isHigherEquals(MCVersion.v1_16)){
                nbtContainer.removeKey("Damage");
            }
            nbt = nbtContainer.toString();
        }
        return NMSUtils.get().getItemRegistryName(itemStack) + " " + itemStack.getAmount() + " " + itemStack.getDurability() + (nbt != null && !nbt.equals("{}") ? " " + nbt : "");
    }
}
