package br.com.finalcraft.evernifecore.itemdatapart;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.itemdatapart.datapart.*;
import br.com.finalcraft.evernifecore.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.version.MCVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//Samaller Version of BossShopPro ItemDataPart
public abstract class ItemDataPart {

    public static int
            PRIORITY_MOST_EARLY = 0,
            PRIORITY_EARLY = 10,
            PRIORITY_NORMAL = 50,
            PRIORITY_LATE = 80,
            PRIORITY_VERY_LATE = 100;

    public static List<ItemDataPart> ALL_REGISTERED_TYPES = new ArrayList<>();

    public static ItemDataPart MATERIAL         = registerType(new ItemDataPartMaterial());
    public static ItemDataPart DURABILITY       = registerType(new ItemDataPartDurability());
    public static ItemDataPart AMOUNT           = registerType(new ItemDataPartAmount());
    public static ItemDataPart CUSTOMMODELDATA  = registerType(new ItemDataPartCustomModelData());
    public static ItemDataPart ITEMFLAGS        = registerType(new ItemDataPartItemflags());
    public static ItemDataPart NAME             = registerType(new ItemDataPartName());
    public static ItemDataPart LORE             = registerType(new ItemDataPartLore());
    public static ItemDataPart NBT              = registerType(new ItemDataPartNBT());

    public static @Nullable <DP extends ItemDataPart> DP registerType(@Nonnull DP type) {
        if (MCVersion.isHigherEquals(type.getMinimumVersion())){
            ALL_REGISTERED_TYPES.add(type);
            return type;
        }
        return null;
    }

    public static ItemDataPart detectTypeSpecial(String whole_line) {
        if (whole_line == null) {
            return null;
        }
        String[] parts = whole_line.split(":", 2);
        String name = parts[0].trim();
        return detectType(name);
    }

    public static ItemDataPart detectType(String s) {
        for (ItemDataPart type : ALL_REGISTERED_TYPES) {
            if (type.isType(s)) {
                return type;
            }
        }
        return null;
    }

    public static ItemStack transformItem(List<String> itemData) {
        return transformItem(new ItemStack(Material.STONE), itemData);
    }

    public static ItemStack transformItem(ItemStack item, List<String> itemdata) {
        Collections.sort(itemdata, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                ItemDataPart type1 = detectTypeSpecial(s1);
                ItemDataPart type2 = detectTypeSpecial(s2);
                if (type1 != null && type2 != null) {
                    return Integer.compare(type1.getPriority(), type2.getPriority());
                }
                return 0;
            }
        });
        for (String line : itemdata) {
            item = transformItem(item, line);
        }
        return item;
    }

    public static ItemStack transformItem(ItemStack item, String line) {
        if (line == null) {
            return item;
        }
        String[] parts = line.split(":", 2);
        String name = parts[0].trim();
        String argument = null;
        if (parts.length == 2) {
            argument = parts[1].trim();
        }

        ItemDataPart part = detectType(name);
        if (part == null) {
            EverNifeCore.getLog().warning("Mistake in Config: Unable to read itemdata '" + name + ":" + argument);
            return item;
        }

        return part.transformItem(item, name, argument);
    }

    public static List<String> readItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        List<String> output = new ArrayList<>();
        for (ItemDataPart part : ALL_REGISTERED_TYPES) {
            try {
                output = part.read(item, output);
            } catch (Exception e) { //Seems like that ItemDataPart is not supported yet
            } catch (NoSuchMethodError e) { //Seems like that ItemDataPart is not supported yet
            }
        }
        return output;
    }

    public static boolean isSimilar(ItemStack base_item, ItemStack other_item, ItemDataPart[] exceptions, boolean compare_amount) {
        if (base_item == null || other_item == null) {
            return false;
        }
        for (ItemDataPart part : ALL_REGISTERED_TYPES) {
            if (isException(exceptions, part)) {
                continue;
            }
            if (!compare_amount && part == AMOUNT) {
                continue;
            }
            try {
                if (!part.isSimilar(base_item, other_item)) {
                    return false;
                }
            } catch (Exception e) { //Seems like that ItemDataPart is not supported yet
            } catch (NoSuchMethodError e) { //Seems like that ItemDataPart is not supported yet
            }
        }
        return true;
    }

    private static boolean isException(ItemDataPart[] exceptions, ItemDataPart part) {
        if (exceptions != null) {
            for (ItemDataPart exception : exceptions) {
                if (exception == part) {
                    return true;
                }
            }
        }
        return false;
    }

    private final String[] names = createNames();

    public boolean isType(String s) {
        if (names != null) {
            for (String name : names) {
                if (name.equalsIgnoreCase(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String name() {
        return names[0].toUpperCase();
    }

    public ItemStack transformItem(ItemStack item, String used_name, String argument) { //Return true in case of success
        if (argument == null && needsArgument()) {
            return item;
        }

        if (removeSpaces() && argument != null) {
            argument = argument.replaceAll(" ", "");
        }

        try {
            return transform(item, used_name.toLowerCase(), argument);
        } catch (NoClassDefFoundError e) { //Seems like that ItemDataPart is not supported yet
            EverNifeCore.getLog().warning("Unable to work with itemdata '" + used_name.toLowerCase() + ":" + argument + ". Seems like it is not supported by your server version yet.");
            return item;
        } catch (NoSuchMethodError e) { //Seems like that ItemDataPart is not supported yet
            EverNifeCore.getLog().warning("Unable to work with itemdata '" + used_name.toLowerCase() + ":" + argument + ". Seems like it is not supported by your server version yet.");
            return item;
        }
    }

    public MCDetailedVersion getMinimumVersion() {
        return MCVersion.v1_7_10.getDetailedVersion();
    }

    public abstract ItemStack transform(ItemStack itemStack, String used_name, String argument);

    public abstract boolean isSimilar(ItemStack base_item, ItemStack other_item);//Return true in case of success

    public abstract List<String> read(ItemStack itemStack, List<String> output);

    public abstract int getPriority(); //Parts with a lower priority (like material) are triggered before parts with higher priority are

    public abstract boolean removeSpaces();

    public abstract String[] createNames();

    public boolean needsArgument() {
        return true; //Can be overriden
    }

}
