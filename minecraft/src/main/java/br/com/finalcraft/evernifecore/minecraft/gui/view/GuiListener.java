package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickKind;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * The one listener of the gui framework - not one per screen, and not one per plugin.
 *
 * <p>{@link #processClick} runs a fixed order, and the order is the point:</p>
 * <ol>
 *   <li><b>Surface guard.</b> A click outside the view never reaches an icon. Only the two actions
 *       that reach into a window from outside it - moving out with shift, gathering with a double
 *       click - are judged, and then only against the screen's policy.</li>
 *   <li><b>Raw slot.</b> An {@code InventoryView} joins two containers and the same slot number
 *       exists in both; only the raw slot names one of them.</li>
 *   <li><b>Policy, before resolving anything.</b> Whatever the policy did not open up is cancelled.
 *       Cancelling the movement and running the handler are independent: a button is a denied take
 *       whose handler still runs.</li>
 *   <li><b>Snapshot.</b> What the container shows has to be what the buffer last wrote there. If it
 *       is not, the click is refused and the screen resyncs - that closes the gap between what was
 *       rendered and what was clicked without paying for item identity in NBT.</li>
 *   <li><b>Dispatch.</b></li>
 * </ol>
 *
 * <p>Concurrency is a token that ages, not a flag: each accepted click makes the previous one stale,
 * so a handler blocked on a future that never completes cannot lock a player out of their own menu.
 * The debounce is separate and purely temporal, and a rejected attempt does not restart its window.</p>
 */
public class GuiListener implements ECListener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        GuiViews.confirmOpen(event.getPlayer().getUniqueId(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        GuiView view = GuiViews.getOpenView(player);
        if (view == null || !view.isSurface(event.getView().getTopInventory())) {
            return;
        }
        processClick(view, event);
    }

    private void processClick(GuiView view, InventoryClickEvent event) {
        String clickTypeName = event.getClick().name();
        String actionName = event.getAction().name();

        Inventory clicked = event.getClickedInventory();
        if (!view.isSurface(clicked)) {
            ClickKind kind = ClickKind.ofAction(actionName);
            boolean reachesIntoTheView = kind == ClickKind.MOVE_TO_OTHER_INVENTORY
                    || kind == ClickKind.COLLECT_TO_CURSOR;
            if (reachesIntoTheView && !view.getGui().getPolicy().allows(clickTypeName, actionName)) {
                event.setCancelled(true);
            }
            return;
        }

        int slot = event.getRawSlot();
        if (!view.getGeometry().isInside(slot)) {
            return;
        }

        ClickPolicy policy = view.getPolicyAt(slot);
        if (!policy.allows(clickTypeName, actionName)) {
            event.setCancelled(true);
        }

        if (!GuiBuffer.isSameOutput(event.getCurrentItem(), view.getBuffer().getCommitted(slot))) {
            event.setCancelled(true);
            view.resync();
            return;
        }

        Icon icon = view.getIconAt(slot);
        if (icon == null || icon.getOnClick() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (view.isWithinDebounce(now)) {
            return;
        }
        view.markClickAccepted(now);

        ClickContext context = view.newClickContext(view.nextClickToken(), slot, event.getClick(),
                event.getCursor(), icon);
        try {
            icon.getOnClick().accept(context);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("A gui click handler failed for [" + view.getViewerName()
                    + "] on slot " + slot + ": " + e);
            e.printStackTrace();
        }

        if (context.isMoveAllowed()) {
            event.setCancelled(false);
        }
    }

    /**
     * A drag that touches the screen is cancelled unless the slots it touches allow it. Vanilla
     * spreads a drag over both containers at once, so a screen with no editable area has to refuse
     * the whole gesture.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        GuiView view = GuiViews.getOpenView(player);
        if (view == null || !view.isSurface(event.getView().getTopInventory())) {
            return;
        }

        int size = view.getGeometry().getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot < size && !view.getPolicyAt(rawSlot).allowsDrag()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            GuiViews.handleClose((Player) event.getPlayer(), event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent event) {
        GuiViews.handleRelease(event.getPlayer(), CloseReason.DISCONNECTED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        GuiViews.handleRelease(event.getPlayer(), CloseReason.WORLD_CHANGED);
    }

}
