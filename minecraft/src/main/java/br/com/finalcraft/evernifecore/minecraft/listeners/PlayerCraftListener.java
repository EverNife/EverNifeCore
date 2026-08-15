package br.com.finalcraft.evernifecore.minecraft.listeners;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.api.events.ECPlayerCraftItemEvent;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCCraftUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCInventoryUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Produces {@link ECPlayerCraftItemEvent}. Registered with the server only while somebody listens to
 * that event - {@link ECListener#registerWhileListened}.
 */
public class PlayerCraftListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        //comes and goes with the listeners of what it produces; logging each turn would be noise
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryCraft(CraftItemEvent event) {

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
                if (cursor != null && cursor.getType() != Material.AIR){
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
                //The bottom half of a crafting view is the crafter's own inventory, so ask the
                //player for it: going through getView() would freeze a call against InventoryView,
                //a type that is a class before 1.21 and an interface after it.
                int capacity = FCInventoryUtil.getMaxFitAmount(event.getRecipe().getResult().clone(), player.getInventory());

                // If we can't fit everything, increase "space" to include the items dropped by
                // crafting
                // (Think: Uncrafting 8 iron blocks into 1 slot)
                //
                // EverNife clarification:
                // For example, if you craft a log into wood you get 4 woods! If in your
                // inventory there is (only) a slot with 63 woods, the crafting will create 4 woods
                // and 3 of them will be dropped on the ground
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

        int craftTimes = recipeAmount / event.getRecipe().getResult().getAmount();
        int amountProduced = recipeAmount;
        ECPlayerCraftItemEvent ecEvent = ECEventBus.global().postIfListened(ECPlayerCraftItemEvent.class,
                () -> new ECPlayerCraftItemEvent(event, player, craftTimes, amountProduced));

        if (ecEvent != null && ecEvent.isCancelled()){
            event.setCancelled(true);
        }

    }

}
