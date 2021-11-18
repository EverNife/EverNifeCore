package br.com.finalcraft.evernifecore.listeners;

import br.com.finalcraft.evernifecore.api.events.ECCraftItemEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCCraftUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class PlayerCraftListener implements ECListener {

    public static boolean HAS_AT_LEAST_ONE_CRAFTITEMEVENT_LISTENER = false;
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryCraft(CraftItemEvent event) {

        if (HAS_AT_LEAST_ONE_CRAFTITEMEVENT_LISTENER == false){
            return;
        }

        switch (event.getAction()) {
            case NOTHING:
            case PLACE_ONE:
            case PLACE_ALL:
            case PLACE_SOME:
                return;
            default:
                break;
        }

        if (event.getSlotType() != InventoryType.SlotType.RESULT){
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)){
            return;
        }

        final Player player = (Player) event.getWhoClicked();

        //For modded servers check
        if (FCBukkitUtil.isFakePlayer(player)) {
            return;
        }

        int recipeAmount = event.getRecipe().getResult().getAmount();

        //This code bellows come from "QuestWorld2"
        switch (event.getClick()) {
            case NUMBER_KEY:
                // If hotbar slot selected is full, crafting fails (vanilla behavior, even when
                // items match)
                if (player.getInventory().getItem(event.getHotbarButton()) != null){
                    recipeAmount = 0;
                }
                break;

            case DROP:
            case CONTROL_DROP:
                // If we are holding items, craft-via-drop fails (vanilla behavior)
                ItemStack cursor = event.getCursor();
                // Apparently, rather than null, an empty cursor is AIR. I don't think that's
                // intended.
                if (cursor == null || cursor.getType() == Material.AIR){
                    recipeAmount = 0;
                }
                break;

            case SHIFT_RIGHT:
            case SHIFT_LEFT:
                // Fixes ezeiger92/QuestWorld2#40
                if (recipeAmount == 0){
                    break;
                }

                int maxCraftable = FCCraftUtil.getMaxCraftAmount(event.getInventory());
                int capacity = FCCraftUtil.getMaxFitAmount(event.getRecipe().getResult().clone(), event.getView().getBottomInventory());

                // If we can't fit everything, increase "space" to include the items dropped by
                // crafting
                // (Think: Uncrafting 8 iron blocks into 1 slot)
                //
                // EverNife clarification:
                // For example, if you craft a log into wood you get 4 woods! If in your
                // inventory there is (only) a slot with 63 woods, the crafting will create 4 woods
                // and 3 of them will be droped on the ground
                if (capacity < maxCraftable){
                    maxCraftable = ((capacity + recipeAmount - 1) / recipeAmount) * recipeAmount;
                }

                recipeAmount = maxCraftable;
                break;
            default:
        }

        // No use continuing if we haven't actually crafted a thing
        if (recipeAmount == 0){
            return;
        }

        ECCraftItemEvent ecEvent = new ECCraftItemEvent(event, player, recipeAmount / event.getRecipe().getResult().getAmount(), recipeAmount);
        Bukkit.getPluginManager().callEvent(ecEvent);

        if (ecEvent.isCancelled()){
            event.setCancelled(true);
        }

    }

}
