package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.event.CraftActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
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
 * A player taking the result out of a crafting grid ({@link EventSource#CRAFT}), with the whole amount the click
 * produces - a shift-click crafts as many as the grid and the inventory allow. A denial, or a cancelled {@code Pre},
 * cancels the craft; one that went through posts its {@link CraftActionEvent} Post. A click that crafts nothing (placing
 * an item on the result slot, a hotbar swap into a full slot) is no craft.
 */
public final class CraftActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        int amount = amountProduced(event);
        if (amount > 0 && Actions.denied(context(event, amount), CraftActionEvent.Pre.class, CraftActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterCraft(CraftItemEvent event) {
        int amount = amountProduced(event);
        if (amount > 0) {
            Actions.post(CraftActionEvent.Post.class, () -> new CraftActionEvent.Post(context(event, amount)));
        }
    }

    private static ItemContext context(CraftItemEvent event, int amount) {
        Player player = (Player) event.getWhoClicked();
        return new ItemContext(EventSource.CRAFT, McActionContexts.position(player.getLocation()), McActionContexts.actor(player), event,
                McActionContexts.key(event.getRecipe().getResult()), amount, null, McActionContexts.item(event.getRecipe().getResult()));
    }

    /** How many items this click takes out of the grid; 0 when it crafts nothing, or for no player in an enabled world. */
    static int amountProduced(CraftItemEvent event) {
        switch (event.getAction()) {
            case NOTHING:
            case PLACE_ONE:
            case PLACE_ALL:
            case PLACE_SOME:
                return 0;
            default:
                break;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT || !(event.getWhoClicked() instanceof Player)) {
            return 0;
        }
        Player player = (Player) event.getWhoClicked();
        if (McActionContexts.isDisabled(player.getWorld())) {
            return 0;
        }
        int recipeAmount = event.getRecipe().getResult().getAmount();
        //the click rules below follow QuestWorld2's craft counting
        switch (event.getClick()) {
            case NUMBER_KEY:
                //a full hotbar slot makes the craft fail (vanilla), even when the items match
                if (player.getInventory().getItem(event.getHotbarButton()) != null) {
                    return 0;
                }
                return recipeAmount;
            case DROP:
            case CONTROL_DROP:
                //holding items on the cursor makes craft-by-drop fail (vanilla); an empty cursor is AIR, not null
                ItemStack cursor = event.getCursor();
                return cursor != null && cursor.getType() != Material.AIR ? 0 : recipeAmount;
            case SHIFT_RIGHT:
            case SHIFT_LEFT:
                if (recipeAmount == 0) {
                    return 0;
                }
                int maxCraftable = FCCraftUtil.getMaxCraftAmount(event.getInventory());
                //the bottom half of a crafting view is the crafter's own inventory: asking the player keeps the call off
                //InventoryView, a class before 1.21 and an interface after it
                int capacity = FCInventoryUtil.getMaxFitAmount(event.getRecipe().getResult().clone(), player.getInventory());
                //what does not fit still gets crafted and dropped (a log into 4 planks next to a slot of 63): round up
                //to whole crafts
                if (capacity < maxCraftable) {
                    maxCraftable = ((capacity + recipeAmount - 1) / recipeAmount) * recipeAmount;
                }
                return maxCraftable;
            default:
                return recipeAmount;
        }
    }
}
