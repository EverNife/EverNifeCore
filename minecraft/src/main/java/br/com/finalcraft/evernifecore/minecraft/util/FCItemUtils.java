package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.everylibs.util.FCInputReader;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FCItemUtils {

    public static @Nullable String getDisplayName(ItemStack itemStack){
        ItemMeta itemMeta = itemStack.hasItemMeta() ? itemStack.getItemMeta() : null;
        if (itemMeta == null || !itemMeta.hasDisplayName()){
            return null;
        }
        return itemMeta.getDisplayName();
    }

    /** The client's name of the item; without an NMS bridge (or in a test), a name read off the material instead. */
    public static @Nonnull String getLocalizedName(ItemStack itemStack){
        if (!NMSUtils.isSupported()){
            return nameOf(itemStack.getType());
        }
        return NMSUtils.get().items().getLocalizedName(itemStack);
    }

    //DIAMOND_SWORD -> "Diamond Sword": English only, so a server with a bridge still answers in the client's language
    private static String nameOf(Material material){
        StringBuilder name = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")){
            if (word.isEmpty()){
                continue;
            }
            if (name.length() > 0){
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return name.toString();
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
        Material material = FCMaterialUtil.parseMaterial(bukkitIdentifier);
        if (material == null){
            throw new IllegalArgumentException("The identifier '" + bukkitIdentifier + "' is not a valid Bukkit Material!");
        }
        ItemStack itemStack = new ItemStack(material);
        if (meta != 0) {
            itemStack.setDurability((short) meta);
        }
        return itemStack;
    }

    /**
     * The stack a {@link #getMinecraftIdentifier(ItemStack) minecraft identifier} describes:
     * {@code <registry name> [amount] [meta] [{snbt}]}, as in {@code minecraft:stone 1 0 {display:{Name:"x"}}}.
     *
     * @throws IllegalArgumentException when the amount or meta is not a number, or no item has that registry name
     */
    public static ItemStack fromMinecraftIdentifier(String minecraftIdentifier){
        MinecraftIdentifier identifier = MinecraftIdentifier.parse(minecraftIdentifier);
        ItemStack itemStack = NMSUtils.get().items().fromRegistryName(identifier.registryName, identifier.amount, identifier.meta);
        if (identifier.snbt == null){
            return itemStack;
        }
        ReadWriteNBT tag = FCNBTUtil.parse(identifier.snbt);
        return FCNBTUtil.modifyItem(itemStack, nbt -> nbt.mergeCompound(tag));
    }

    /** The parts of a minecraft identifier, split but not resolved. */
    static final class MinecraftIdentifier {
        final String registryName;
        final int amount;
        final int meta;
        final @Nullable String snbt;

        private MinecraftIdentifier(String registryName, int amount, int meta, @Nullable String snbt) {
            this.registryName = registryName;
            this.amount = amount;
            this.meta = meta;
            this.snbt = snbt;
        }

        static MinecraftIdentifier parse(String minecraftIdentifier){
            //the snbt is the whole rest of the line: it may carry spaces of its own
            String[] parts = minecraftIdentifier.trim().split(" ", 4);
            return new MinecraftIdentifier(parts[0],
                    parts.length >= 2 ? parseNumber(parts[1], "amount", minecraftIdentifier) : 1,
                    parts.length >= 3 ? parseNumber(parts[2], "meta", minecraftIdentifier) : 0,
                    parts.length >= 4 ? parts[3].trim() : null);
        }

        private static int parseNumber(String value, String part, String minecraftIdentifier){
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The " + part + " '" + value + "' of the minecraft identifier '" + minecraftIdentifier
                        + "' is not a whole number. The form is '<registry name> [amount] [meta] [{snbt}]', as in 'minecraft:stone 1 0'.", e);
            }
        }
    }

    public static String getMinecraftIdentifier(ItemStack itemStack){
        return getMinecraftIdentifier(itemStack, true);
    }

    public static String getMinecraftIdentifier(ItemStack itemStack, boolean withNbt){
        String nbt = null;
        itemStack = NMSUtils.get().items().validateHandle(itemStack);
        if (withNbt){
            ReadWriteNBT nbtContainer = FCNBTUtil.parse(FCNBTUtil.readItem(itemStack).toString());
            if (MCVersion.isHigherEquals(MCVersion.v1_16)){
                nbtContainer.removeKey("Damage");
            }
            nbt = nbtContainer.toString();
        }
        return NMSUtils.get().items().getRegistryName(itemStack) + " " + itemStack.getAmount() + " " + itemStack.getDurability() + (nbt != null && !nbt.equals("{}") ? " " + nbt : "");
    }
}
