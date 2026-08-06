package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiListener;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

/**
 * Delivers a platform event straight to the framework's single listener.
 *
 * <p>There is no plugin manager here on purpose: what is under test is what the listener does with
 * an event, not whether Bukkit can route one.</p>
 */
public final class GuiEventBus {

    private final GuiListener listener = new GuiListener();

    public GuiListener getListener() {
        return listener;
    }

    public void fireOpen(InventoryView view) {
        listener.onInventoryOpen(new InventoryOpenEvent(view));
    }

    public void fireClose(InventoryView view) {
        listener.onInventoryClose(new InventoryCloseEvent(view));
    }

    public void fireQuit(Player player) {
        listener.onPlayerQuit(new PlayerQuitEvent(player, ""));
    }

}
