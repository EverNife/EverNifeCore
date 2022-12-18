package br.com.finalcraft.evernifecore.integration.everforgelib;

import br.com.finalcraft.everforgelib.integration.ModHookArmourersWorkshop;
import br.com.finalcraft.everforgelib.integration.ModHookBaubles;
import br.com.finalcraft.everforgelib.integration.ModHookDraconicEvolution;
import br.com.finalcraft.everforgelib.integration.ModHookTinkersConstruct;
import br.com.finalcraft.everforgelib.integration.data.AWShopSubInventory;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.commons.SimpleEntry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//Todo Remove this from EverNifeCore
//The best will be to place all this into a separeted plugin!
public class EverForgeLibIntegration {

    public static boolean apiLoaded = false;
    public static boolean baublesLoaded = false;
    public static boolean tinkersLoaded = false;
    public static boolean draconicLoaded = false;
    public static boolean armourersWorkShopLoaded = false;

     static {
        try {
            Class.forName("br.com.finalcraft.everforgelib.EverForgeLib");
            EverNifeCore.info("Found EverForgeLib... searching for mods to integrate!");
            apiLoaded = true;

            if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.everforgelib.integration.ModHookBaubles") && ModHookBaubles.isHooked()){
                baublesLoaded = true;
                EverNifeCore.info("[EVERFORLIB-HOOKING] - Boubles Enabled!");
            }

            if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.everforgelib.integration.ModHookTinkersConstruct") && ModHookTinkersConstruct.isHooked()){
                tinkersLoaded = true;
                EverNifeCore.info("[EVERFORLIB-HOOKING] - TinkersConstruct Enabled!");
            }

            if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.everforgelib.integration.ModHookDraconicEvolution") && ModHookDraconicEvolution.isHooked()){
                draconicLoaded = true;
                EverNifeCore.info("[EVERFORLIB-HOOKING] - DraconicEvolution Enabled!");
            }

            if (FCBukkitUtil.isClassLoaded("br.com.finalcraft.everforgelib.integration.ModHookArmourersWorkshop") && ModHookArmourersWorkshop.isHooked()){
                armourersWorkShopLoaded = true;
                EverNifeCore.info("[EVERFORLIB-HOOKING] - ArmourersWorkshop Enabled!");
            }
        }catch (Exception ignored){}
    }

    public static boolean isApiLoaded() {
        return apiLoaded;
    }


    // -----------------------------------------------------------------------------------------------------------------
    //  Baubles Mod Hook
    // -----------------------------------------------------------------------------------------------------------------

    public static ItemStack[] getBaublesInventory(Player player){
        if (!baublesLoaded) return null;
        Object[] objects = ModHookBaubles.getPlayerInventoryAsOBJ(player.getName());
        ItemStack[] itemStacks = new ItemStack[objects.length];
        for (int i = 0; i < objects.length; i++) {
            itemStacks[i] = objects[i] == null ? null : NMSUtils.get().asItemStack(objects[i]);
        }
        return itemStacks;
    }

    public static void setBaublesInventory(Player player, ItemStack[] inventory){
        if (!baublesLoaded) return;
        Object[] objects = new Object[inventory.length];
        for (int i = 0; i < inventory.length; i++) {
            objects[i] = inventory[i] == null ? null : NMSUtils.get().asMinecraftItemStack(inventory[i]);
        }
        ModHookBaubles.setPlayerInventoryAsOBJ(player.getName(), objects);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Tinkers Mod Hook
    // -----------------------------------------------------------------------------------------------------------------

    public static ItemStack[] getTinkersInventory(Player player){
        if (!tinkersLoaded) return null;
        Object[] objects = ModHookTinkersConstruct.getPlayerInventoryAsOBJ(player.getName());
        ItemStack[] itemStacks = new ItemStack[objects.length];
        for (int i = 0; i < objects.length; i++) {
            itemStacks[i] = objects[i] == null ? null : NMSUtils.get().asItemStack(objects[i]);
        }
        return itemStacks;
    }

    public static void setTinkersInventory(Player player, ItemStack[] inventory){
        if (!tinkersLoaded) return;
        Object[] objects = new Object[inventory.length];
        for (int i = 0; i < inventory.length; i++) {
            objects[i] = inventory[i] == null ? null : NMSUtils.get().asMinecraftItemStack(inventory[i]);
        }
        ModHookTinkersConstruct.setPlayerInventoryAsOBJ(player.getName(), objects);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Draconic Mod Hook
    // -----------------------------------------------------------------------------------------------------------------

    public static ItemStack[] getDraconicChestIventory(ItemStack draconicChest){
        if (!draconicLoaded) return null;
        Object[] objects = ModHookDraconicEvolution.getDraconicInventoryAsOBJ(NMSUtils.get().asMinecraftItemStack(draconicChest));
        ItemStack[] itemStacks = new ItemStack[objects.length];
        for (int i = 0; i < objects.length; i++) {
            itemStacks[i] = objects[i] == null ? null : NMSUtils.get().asItemStack(objects[i]);
        }
        return itemStacks;
    }

    public static ItemStack setDraconicChestInventory(ItemStack draconicChest, ItemStack[] inventory){
        if (!draconicLoaded) return null;
        Object[] objects = new Object[inventory.length];
        for (int i = 0; i < inventory.length; i++) {
            objects[i] = inventory[i] == null ? null : NMSUtils.get().asMinecraftItemStack(inventory[i]);
        }
        Object mcItemStack = NMSUtils.get().asMinecraftItemStack(draconicChest);
        ModHookDraconicEvolution.setDraconicInventoryAsOBJ(mcItemStack, objects);
        return NMSUtils.get().asItemStack(mcItemStack);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  ArmourersWorkshop Mod Hook
    // -----------------------------------------------------------------------------------------------------------------

    public static List<Map.Entry<String, ItemStack[]>> getArmourersWorkshopInventory(Player player){
        return ModHookArmourersWorkshop.getPlayerInventoryAsOBJ(player.getName())
                .stream()
                .map(subInventory -> {
                    return SimpleEntry.of(
                            subInventory.getId(),
                            Arrays.stream(subInventory.getItemStacks())
                                    .map(o -> o == null ? null : NMSUtils.get().asItemStack(o))
                                    .collect(Collectors.toList())
                                    .toArray(new ItemStack[0])
                    );
                })
                .collect(Collectors.toList());
    }

    public static void setArmourersWorkshopInventory(Player player, List<Map.Entry<String, ItemStack[]>> subInventories){
        ModHookArmourersWorkshop.setPlayerInventoryAsOBJ(player.getName(), subInventories.stream().map(entry -> {
            return new AWShopSubInventory(
                    entry.getKey(),
                    Arrays.stream(entry.getValue())
                            .map(itemStack -> itemStack == null ? null : NMSUtils.get().asMinecraftItemStack(itemStack))
                            .collect(Collectors.toList())
                            .toArray(new Object[0])
            );
        }).collect(Collectors.toList()));
    }
}
