package br.com.finalcraft.evernifecore.integration.everforgelib;

import br.com.finalcraft.everforgelib.integration.ModHookBaubles;
import br.com.finalcraft.everforgelib.integration.ModHookDraconicEvolution;
import br.com.finalcraft.everforgelib.integration.ModHookTinkersConstruct;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EverForgeLibIntegration {

    public static boolean apiLoaded = false;
    public static boolean baublesLoaded = false;
    public static boolean tinkersLoaded = false;
    public static boolean draconicLoaded = false;

    public static void initialize(){
        try {
            Class.forName("br.com.finalcraft.everforgelib.EverForgeLib");
            EverNifeCore.info("Found EverForgeLib... searching for mods to integrate!");
            apiLoaded = true;
            if (baublesLoaded = ModHookBaubles.isHooked()) EverNifeCore.info("[EVERFORLIB-HOOKING] - Boubles Enabled!");
            if (tinkersLoaded = ModHookTinkersConstruct.isHooked()) EverNifeCore.info("[EVERFORLIB-HOOKING] - TinkersConstruct Enabled!");
            if (draconicLoaded = ModHookDraconicEvolution.isHooked()) EverNifeCore.info("[EVERFORLIB-HOOKING] - DraconicEvolution Enabled!");
        }catch (Exception ignored){}
    }

    public static boolean isApiLoaded() {
        return apiLoaded;
    }

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
}
