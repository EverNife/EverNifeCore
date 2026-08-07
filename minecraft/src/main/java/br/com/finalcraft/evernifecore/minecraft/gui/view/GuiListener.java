package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickKind;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 *   <li><b>Editable areas first.</b> A slot the player owns is judged by that area's own policy and
 *       item filter and then left alone: it carries no icon to dispatch, and the snapshot check below
 *       does not apply to a slot the framework deliberately stopped drawing.</li>
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
        //getInventory() IS the view's top inventory, and it is declared by the event class, which
        //never changed shape - reaching it through getView() would freeze a call against
        //InventoryView, a type that is a class before 1.21 and an interface after it.
        if (view == null || !view.isSurface(event.getInventory())) {
            return;
        }
        processClick(view, event);
    }

    private void processClick(GuiView view, InventoryClickEvent event) {
        String clickTypeName = event.getClick().name();
        String actionName = event.getAction().name();
        ClickKind kind = ClickKind.ofAction(actionName);

        //a double click gathers every matching stack of the whole window at once, this screen's own icons
        //included, which no per-slot rule can express - an editable screen refuses it wherever it is aimed
        if (kind == ClickKind.COLLECT_TO_CURSOR && view.hasStorage()) {
            event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (!view.isSurface(clicked)) {
            if (kind == ClickKind.MOVE_TO_OTHER_INVENTORY && view.hasStorage()) {
                moveIntoStorage(view, event, clickTypeName);
                return;
            }
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

        StorageView storage = view.getStorageAt(slot);
        if (storage != null) {
            processStorageClick(storage, event, clickTypeName, kind);
            return;
        }

        ClickPolicy policy = view.getPolicyAt(slot);
        if (!policy.allowsKind(clickTypeName, kind)) {
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
     * A click inside an editable area: the area's policy says whether the gesture goes through and its
     * filter whether the item may come in. Nothing else happens - the platform moves the item, and the
     * area reads the result back on the next tick.
     */
    private void processStorageClick(StorageView storage, InventoryClickEvent event, String clickTypeName,
                                     ClickKind kind) {
        //a shift-move out of the region is a take; the place is the one arriving from the inventory below
        ClickKind judged = kind == ClickKind.MOVE_TO_OTHER_INVENTORY ? ClickKind.TAKE : kind;
        if (!storage.getPolicy().allowsKind(clickTypeName, judged) || !incomingIsAllowed(storage, event, kind)) {
            event.setCancelled(true);
            return;
        }
        storage.scheduleSync();
    }

    /** Whether what this click would put INTO the region is an item that region accepts. */
    private static boolean incomingIsAllowed(StorageView storage, InventoryClickEvent event, ClickKind kind) {
        if (!storage.hasPlaceFilter()) {
            return true;
        }
        if (kind == ClickKind.PLACE || kind == ClickKind.SWAP) {
            return storage.mayHold(event.getCursor());
        }
        if (kind == ClickKind.HOTBAR) {
            PlayerInventory inventory = event.getWhoClicked().getInventory();
            int button = event.getHotbarButton();
            //an item the filter cannot even read is one that does not get in
            return inventory != null && button >= 0 && storage.mayHold(inventory.getItem(button));
        }
        return true;
    }

    /**
     * A shift-click aimed at the screen from the inventory below it.
     *
     * <p>The platform would spread the stack over any free slot of the window, this screen's buttons
     * included, so the gesture is cancelled and the framework pours the stack into the editable areas
     * itself - in declaration order, as much as each accepts, and never more than it takes off the slot
     * the stack came from.</p>
     */
    private void moveIntoStorage(GuiView view, InventoryClickEvent event, String clickTypeName) {
        event.setCancelled(true);
        ItemStack moving = event.getCurrentItem();
        if (GuiBuffer.isEmpty(moving)) {
            return;
        }

        int carried = moving.getAmount();
        int left = carried;
        for (StorageView storage : view.getStorages()) {
            if (left <= 0) {
                break;
            }
            if (!storage.getPolicy().allowsKind(clickTypeName, ClickKind.PLACE)) {
                continue;
            }
            int placed = storage.pourIn(moving, left);
            if (placed > 0) {
                left -= placed;
                storage.scheduleSync();
            }
        }
        if (left == carried) {
            return;
        }

        event.setCurrentItem(left <= 0 ? null : amountOf(moving, left));
        ((Player) event.getWhoClicked()).updateInventory();
    }

    /**
     * A drag is taken apart slot by slot: the slots that accept it receive their share and the rest
     * receive nothing, rather than one refused slot cancelling the gesture for all of them.
     *
     * <p>The platform spreads a drag over both containers at once and offers no way to drop part of it, so
     * a gesture that is not accepted whole is cancelled and the accepted share is written here instead -
     * and only into an editable area. A slot the buffer still owns is drawn by the screen, so a dragged
     * item written there would be an item the next render erases; a mixed gesture touching one is refused
     * entirely, as it always was. Whatever is not placed stays on the cursor.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        GuiView view = GuiViews.getOpenView(player);
        if (view == null || !view.isSurface(event.getInventory())) {
            return;
        }

        ItemStack dragged = event.getOldCursor();
        int size = view.getGeometry().getSize();
        List<Integer> shares = new ArrayList<>();
        Set<StorageView> touched = new LinkedHashSet<>();
        boolean refusedAny = false;

        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot == null || rawSlot < 0 || rawSlot >= size) {
                continue;
            }
            StorageView storage = view.getStorageAt(rawSlot);
            boolean allowed = view.getPolicyAt(rawSlot).allowsDrag()
                    && (storage == null || storage.mayHold(dragged));
            if (!allowed) {
                refusedAny = true;
                continue;
            }
            if (storage != null) {
                shares.add(rawSlot);
                touched.add(storage);
            }
        }

        if (!refusedAny) {
            for (StorageView storage : touched) {
                storage.scheduleSync();
            }
            return;
        }

        event.setCancelled(true);
        if (shares.isEmpty() || !applyDragShares(view, player, event, dragged, shares)) {
            return;
        }
        for (StorageView storage : touched) {
            storage.scheduleSync();
        }
    }

    /**
     * Writes the accepted share of a cancelled drag and takes exactly that much off the cursor.
     *
     * @return whether anything was placed at all
     */
    private static boolean applyDragShares(GuiView view, Player player, InventoryDragEvent event,
                                           ItemStack dragged, List<Integer> shares) {
        if (GuiBuffer.isEmpty(dragged)) {
            return false;
        }
        int carried = dragged.getAmount();
        int placed = 0;
        for (Integer rawSlot : shares) {
            if (placed >= carried) {
                break;
            }
            StorageView storage = view.getStorageAt(rawSlot);
            int share = Math.min(plannedShare(view, event, rawSlot, dragged), carried - placed);
            placed += storage.addAt(rawSlot, dragged, share);
        }
        if (placed <= 0) {
            return false;
        }

        ItemStack remaining = placed >= carried
                ? new ItemStack(Material.AIR)
                : amountOf(dragged, carried - placed);
        //both, because a cancelled drag leaves the cursor to the platform: whichever of the two it reads,
        //it reads the amount that is actually left
        event.setCursor(remaining);
        player.setItemOnCursor(remaining);
        player.updateInventory();
        return true;
    }

    /** How much of the drag the platform had planned for one slot: what it would end with, less what it holds. */
    private static int plannedShare(GuiView view, InventoryDragEvent event, int rawSlot, ItemStack dragged) {
        ItemStack planned = event.getNewItems().get(rawSlot);
        if (planned == null) {
            return 0;
        }
        ItemStack current = view.getSurface().getItem(rawSlot);
        int held = GuiBuffer.isEmpty(current) || !current.isSimilar(dragged) ? 0 : current.getAmount();
        return planned.getAmount() - held;
    }

    private static ItemStack amountOf(ItemStack item, int amount) {
        ItemStack sized = item.clone();
        sized.setAmount(amount);
        return sized;
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
