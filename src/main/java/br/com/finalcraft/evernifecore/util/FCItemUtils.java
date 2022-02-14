package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public static @NotNull String getLocalizedName(ItemStack itemStack){
        return NMSUtils.get().getLocalizedName(itemStack);
    }

    public static void setDisplayName(ItemStack itemStack, String displayName){
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(displayName);
        itemStack.setItemMeta(itemMeta);
    }

    public static @NotNull List<String> getLore(ItemStack itemStack){
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

    @Deprecated
    public static ItemStack getCustomItem(Material material, int amount, byte meta, @Nullable String displayName, @Nullable List<String> loreLines) {
        ItemStack itemStack = new ItemStack(material, amount, meta);
        if (displayName != null || loreLines != null){
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (displayName != null) itemMeta.setDisplayName(displayName);
            if (loreLines != null) itemMeta.setLore(loreLines);
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }

    @Deprecated
    public static ItemStack getCustomItem(Material material, int amount, @Nullable String displayName, @Nullable List<String> loreLines) {
        return getCustomItem(material, amount, (byte) 0, displayName, loreLines);
    }

    @Deprecated
    public static ItemStack getCustomItem(Material material, int amount, @Nullable String displayName) {
        return getCustomItem(material, amount, (byte) 0, displayName, null);
    }

    @Deprecated
    public static ItemStack getCustomItem(Material material, int amount, @Nullable List<String> loreLines) {
        return getCustomItem(material, amount, (byte) 0, null, loreLines);
    }

    @Deprecated
    public static ItemStack getCustomItem(Material material, Integer amount, String name) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(name);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    @Deprecated
    public static ItemStack getCustomItem(ItemStack itemStack, List<String> loreLines) {
        ItemStack clone = itemStack.clone();
        ItemMeta itemMeta = clone.getItemMeta();
        itemMeta.setLore(loreLines);
        clone.setItemMeta(itemMeta);

        return clone;
    }

    @Deprecated
    public static ItemStack getCustomItem(ItemStack itemStack, String name) {
        ItemStack clone = itemStack.clone();
        ItemMeta itemMeta = clone.getItemMeta();
        itemMeta.setDisplayName(name);
        clone.setItemMeta(itemMeta);

        return clone;
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
        if (withNbt){
            nbt = NMSUtils.get().getNBTtoString(itemStack);
        }
        return NMSUtils.get().getItemRegistryName(itemStack) + " " + itemStack.getAmount() + " " + itemStack.getDurability() + (nbt != null ? " " + nbt : "");
    }
}
