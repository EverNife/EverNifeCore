package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FCItemUtils {

    public static String getDisplayName(ItemStack itemStack){
        if (itemStack.hasItemMeta()){
            ItemMeta itemMeta = itemStack.getItemMeta();
            return itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : null;
        }
        return null;
    }

    public static String getLocalizedName(ItemStack itemStack){
        return NMSUtils.get().getLocalizedName(itemStack);
    }

    public static void setDisplayName(ItemStack itemStack, String displayName){
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(displayName);
        itemStack.setItemMeta(itemMeta);
    }

    public static List<String> getLore(ItemStack itemStack){
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.hasLore() ? itemMeta.getLore() : new ArrayList<String>();
    }

    public static void setLore(ItemStack itemStack, List<String> loreLines){
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setLore(loreLines);
        itemStack.setItemMeta(itemMeta);
    }

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

    public static ItemStack getCustomItem(Material material, int amount, @Nullable String displayName, @Nullable List<String> loreLines) {
        return getCustomItem(material, amount, (byte) 0, displayName, loreLines);
    }

    public static ItemStack getCustomItem(Material material, int amount, @Nullable String displayName) {
        return getCustomItem(material, amount, (byte) 0, displayName, null);
    }

    public static ItemStack getCustomItem(Material material, int amount, @Nullable List<String> loreLines) {
        return getCustomItem(material, amount, (byte) 0, null, loreLines);
    }

    public static ItemStack getCustomItem(Material material, Integer amount, String name) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(name);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    public static ItemStack getCustomItem(ItemStack itemStack, List<String> loreLines) {
        ItemStack clone = itemStack.clone();
        ItemMeta itemMeta = clone.getItemMeta();
        itemMeta.setLore(loreLines);
        clone.setItemMeta(itemMeta);

        return clone;
    }

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

    public static ItemStack fromBukkitIdentifier(String bukkitIdentifier){
        int meta = 0;
        if (bukkitIdentifier.contains(":")){
            try {
                String[] split = bukkitIdentifier.split(Pattern.quote(":"));
                bukkitIdentifier = split[0];
                meta = Short.parseShort(split[1]);
                if (meta < 0) meta = 0;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        ItemStack itemStack = new ItemStack(Material.valueOf(bukkitIdentifier));
        if (meta != 0) itemStack.setDurability((short) meta);
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
