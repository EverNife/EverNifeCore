package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickKind;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one listener of the chest renderer - not one per screen, and not one per plugin.
 *
 * <p>{@link #processClick} runs a fixed order, and the order is the point:</p>
 * <ol>
 *   <li><b>Surface guard.</b> A click outside the window never reaches an icon. Only the two actions that reach into a
 *       window from outside it - moving out with shift, gathering with a double click - are judged, and then only
 *       against the screen's policy.</li>
 *   <li><b>Raw slot.</b> An {@code InventoryView} joins two containers and the same slot number exists in both; only the
 *       raw slot names one of them.</li>
 *   <li><b>Editable areas first.</b> A slot the player owns is judged by that area's own policy and item filter and then
 *       left alone: it carries no icon, and the snapshot check below does not apply to a slot the screen stopped
 *       drawing.</li>
 *   <li><b>Policy, before resolving anything.</b> Whatever the policy did not open up is cancelled. Cancelling the
 *       movement and running the handler are independent: a button is a denied take whose handler still runs.</li>
 *   <li><b>Snapshot.</b> What the container shows has to be what the surface last wrote there. If it is not, the click
 *       is refused and the screen resyncs.</li>
 *   <li><b>Dispatch</b>, through {@link ScreenView#click}: the gesture and the kind are read by NAME, so a 1.7.10
 *       server and a 1.21 server go through the same code.</li>
 * </ol>
 */
public class BukkitScreenListener implements ECListener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        ChestScreenRenderer.confirmOpen(event.getPlayer().getUniqueId(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        ScreenView view = Screens.getOpenView(event.getWhoClicked().getUniqueId());
        //getInventory() IS the view's top inventory, declared by the event class, which never changed shape - reaching
        //it through getView() would freeze a call against InventoryView, a class before 1.21 and an interface after
        BukkitScreenSurface surface = ChestScreenRenderer.surfaceOf(view);
        if (surface == null || !view.isSurface(event.getInventory())) {
            return;
        }
        processClick(view, surface, event);
    }

    private void processClick(ScreenView view, BukkitScreenSurface surface, InventoryClickEvent event) {
        String clickTypeName = event.getClick().name();
        String actionName = event.getAction().name();
        ClickKind kind = ClickKind.ofAction(actionName);
        boolean hasStorage = view.hasStorage();

        //a double click gathers every matching stack of the whole window at once, this screen's own icons included,
        //which no per-slot rule can express - an editable screen refuses it wherever it is aimed
        if (kind == ClickKind.COLLECT_TO_CURSOR && hasStorage) {
            event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (!view.isSurface(clicked)) {
            if (kind == ClickKind.MOVE_TO_OTHER_INVENTORY && hasStorage) {
                moveIntoStorage(surface, event, clickTypeName);
                return;
            }
            boolean reachesIntoTheView = kind == ClickKind.MOVE_TO_OTHER_INVENTORY || kind == ClickKind.COLLECT_TO_CURSOR;
            if (reachesIntoTheView && !view.getScreen().getPolicy().allows(clickTypeName, actionName)) {
                event.setCancelled(true);
            }
            return;
        }

        int slot = event.getRawSlot();
        if (!view.getGrid().isInside(slot)) {
            return;
        }

        StorageView storage = surface.getStorageAt(slot);
        if (storage != null) {
            processStorageClick(storage, event, clickTypeName, kind);
            return;
        }

        boolean policyAllows = view.getPolicyAt(slot).allowsKind(clickTypeName, kind);
        if (!policyAllows) {
            event.setCancelled(true);
        }

        if (!surface.isShowingWritten(slot, event.getCurrentItem())) {
            event.setCancelled(true);
            view.resync();
            return;
        }

        boolean moveAllowed = view.click(slot, ClickGesture.ofName(clickTypeName), kind, ChestItems.specOf(event.getCursor()));
        if (moveAllowed && !policyAllows) {
            event.setCancelled(false); //the handler allowed what the policy did not
        }
    }

    /**
     * A click inside an editable area: the area's policy says whether the gesture goes through, its filter whether the
     * item may come in, and its store - when the store judges its own updates - what the slot is about to become. The
     * platform moves the item; the area reads the result back on the next tick.
     */
    private void processStorageClick(StorageView storage, InventoryClickEvent event, String clickTypeName, ClickKind kind) {
        //a shift-move out of the region is a take; the place is the one arriving from the inventory below
        ClickKind judged = kind == ClickKind.MOVE_TO_OTHER_INVENTORY ? ClickKind.TAKE : kind;
        if (!storage.getPolicy().allowsKind(clickTypeName, judged) || !incomingIsAllowed(storage, event, kind)
                || !storeAcceptsClick(storage, event)) {
            event.setCancelled(true);
            return;
        }
        storage.scheduleSync();
    }

    /** Whether the store agrees to what this click is about to leave in the slot. */
    private static boolean storeAcceptsClick(StorageView storage, InventoryClickEvent event) {
        if (!storage.vetsUpdates()) {
            return true;
        }
        SlotOutcome outcome = SlotOutcome.afterClick(event.getAction().name(), event.getCurrentItem(),
                event.getCursor(), hotbarItemOf(event));
        if (outcome.isUnchanged()) {
            return true;
        }
        return outcome.isKnown() && storage.mayAccept(event.getRawSlot(), outcome.getItem());
    }

    /** What the number key pressed on this click points at, or {@code null} when none was. */
    private static ItemStack hotbarItemOf(InventoryClickEvent event) {
        int button = event.getHotbarButton();
        return button < 0 ? null : event.getWhoClicked().getInventory().getItem(button);
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
            int button = event.getHotbarButton();
            return button >= 0 && storage.mayHold(event.getWhoClicked().getInventory().getItem(button));
        }
        return true;
    }

    /**
     * A shift-click aimed at the screen from the inventory below it. The platform would spread the stack over any free
     * slot of the window, this screen's buttons included, so the gesture is cancelled and the stack is poured into the
     * editable areas here - in declaration order, as much as each accepts, never more than the slot it came from held.
     */
    private void moveIntoStorage(BukkitScreenSurface surface, InventoryClickEvent event, String clickTypeName) {
        event.setCancelled(true);
        ItemStack moving = event.getCurrentItem();
        if (ItemStore.isEmpty(moving)) {
            return;
        }

        int carried = moving.getAmount();
        int left = carried;
        for (StorageView storage : surface.getStorages()) {
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
     * A drag is taken apart slot by slot: the slots that accept it receive their share and the rest receive nothing,
     * rather than one refused slot cancelling the gesture for all of them.
     *
     * <p>The platform spreads a drag over both containers at once and offers no way to drop part of it, so a gesture
     * not accepted whole is cancelled and the accepted share is written here - and only into an editable area. A slot
     * the screen draws would have a dragged item erased by the next render, so a gesture touching one is refused
     * entirely. Whatever is not placed stays on the cursor. A store that judges its own updates never has a drag
     * applied by the platform: each slot is asked with the amount it is about to receive, the only moment that number
     * exists.</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ScreenView view = Screens.getOpenView(player.getUniqueId());
        BukkitScreenSurface surface = ChestScreenRenderer.surfaceOf(view);
        if (surface == null || !view.isSurface(event.getInventory())) {
            return;
        }

        ItemStack dragged = event.getOldCursor();
        int size = view.getGrid().getSize();
        List<Integer> shares = new ArrayList<>();
        Set<StorageView> touched = new LinkedHashSet<>();
        boolean refusedAny = false;
        boolean divideHere = false;

        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot == null || rawSlot < 0 || rawSlot >= size) {
                continue;
            }
            StorageView storage = surface.getStorageAt(rawSlot);
            boolean allowed = view.getPolicyAt(rawSlot).allowsDrag() && (storage == null || storage.mayHold(dragged));
            if (!allowed) {
                refusedAny = true;
                continue;
            }
            if (storage != null) {
                shares.add(rawSlot);
                touched.add(storage);
                divideHere |= storage.vetsUpdates();
            } else if (view.getBuffer().owns(rawSlot)) {
                refusedAny = true;   //the screen draws this slot; a drag written here is erased next render
            }
        }

        if (!refusedAny && !divideHere) {
            for (StorageView storage : touched) {
                storage.scheduleSync();
            }
            return;
        }

        event.setCancelled(true);
        if (shares.isEmpty() || !applyDragShares(surface, player, event, dragged, shares)) {
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
    private static boolean applyDragShares(BukkitScreenSurface surface, Player player, InventoryDragEvent event,
                                           ItemStack dragged, List<Integer> shares) {
        if (ItemStore.isEmpty(dragged)) {
            return false;
        }
        int carried = dragged.getAmount();
        int placed = 0;
        for (Integer rawSlot : shares) {
            if (placed >= carried) {
                break;
            }
            StorageView storage = surface.getStorageAt(rawSlot);
            int share = Math.min(plannedShare(surface, event, rawSlot, dragged), carried - placed);
            placed += storage.addAt(rawSlot, dragged, share);
        }
        if (placed <= 0) {
            return false;
        }

        ItemStack remaining = placed >= carried ? new ItemStack(Material.AIR) : amountOf(dragged, carried - placed);
        //both, because a cancelled drag leaves the cursor to the platform: whichever it reads, it reads what is left
        event.setCursor(remaining);
        player.setItemOnCursor(remaining);
        player.updateInventory();
        return true;
    }

    /** How much of the drag the platform had planned for one slot: what it would end with, less what it holds. */
    private static int plannedShare(BukkitScreenSurface surface, InventoryDragEvent event, int rawSlot, ItemStack dragged) {
        ItemStack planned = event.getNewItems().get(rawSlot);
        if (planned == null) {
            return 0;
        }
        ItemStack current = surface.getItem(rawSlot);
        int held = ItemStore.isEmpty(current) || !current.isSimilar(dragged) ? 0 : current.getAmount();
        return planned.getAmount() - held;
    }

    private static ItemStack amountOf(ItemStack item, int amount) {
        ItemStack sized = item.clone();
        sized.setAmount(amount);
        return sized;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent event) {
        ScreenView view = Screens.getOpenView(event.getPlayer().getUniqueId());
        BukkitScreenSurface surface = ChestScreenRenderer.surfaceOf(view);
        if (surface == null || surface.isSwapping() || !view.isSurface(event.getInventory())) {
            return;
        }
        Screens.windowClosed(view, CloseReason.PLAYER_CLOSED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Screens.viewerGone(event.getPlayer().getUniqueId(), CloseReason.DISCONNECTED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Screens.viewerGone(event.getPlayer().getUniqueId(), CloseReason.WORLD_CHANGED);
    }

}
