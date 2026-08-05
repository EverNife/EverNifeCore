package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.scheduler.McFCScheduler;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Every open {@link GuiView}, and the only way one comes into being.
 *
 * <p>A view is registered when the server has <b>confirmed</b> the window, never when {@code open()}
 * was called. A refused open - the player is sleeping, is leaving, another plugin cancelled it -
 * therefore leaves nothing behind: no registration, no task, no retained {@code Player}.</p>
 *
 * <p>Titles longer than 32 characters are cut on 1.8 and older, where the window packet cannot carry
 * them and the server throws instead.</p>
 */
public final class GuiViews {

    private static final int LEGACY_TITLE_LIMIT = 32;

    private static final Map<UUID, GuiView> OPEN = new LinkedHashMap<>();

    /** The open in flight. {@code openInventory} fires its event before returning, so one slot is enough. */
    private static PendingOpen pending;

    private GuiViews() {

    }

    /** The screen {@code player} currently has open, or {@code null}. */
    @Nullable
    public static GuiView getOpenView(@Nullable Player player) {
        return player == null ? null : OPEN.get(player.getUniqueId());
    }

    public static int getOpenCount() {
        return OPEN.size();
    }

    /** See {@link Gui#open(Player)}. */
    @Nonnull
    public static CompletableFuture<GuiView> open(@Nonnull Gui gui, @Nonnull Player player) {
        CompletableFuture<GuiView> future = new CompletableFuture<>();
        if (FCBukkitUtil.isMainThread()) {
            openOnMainThread(gui, player, future);
        } else {
            McFCScheduler.INSTANCE.runSync(() -> openOnMainThread(gui, player, future));
        }
        return future;
    }

    private static void openOnMainThread(Gui gui, Player player, CompletableFuture<GuiView> future) {
        try {
            if (!player.isOnline()) {
                refuse(gui, player, future, "the player is no longer online");
                return;
            }

            String title = trimTitle(gui.getTitle());
            Inventory inventory = createInventory(gui, title);
            GuiView view = new GuiView(gui, player, new BukkitGuiSurface(inventory),
                    BukkitGuiScheduler.INSTANCE, gui.getTitle());

            if (!attemptOpen(player, inventory)) {
                refuse(gui, player, future, "the server did not open the window "
                        + "(the player may be sleeping or leaving, or another plugin cancelled the open)");
                return;
            }

            GuiView displaced = OPEN.put(player.getUniqueId(), view);
            if (displaced != null) {
                displaced.release(CloseReason.REQUESTED);
            }

            view.start();
            view.render();
            view.commitNow();
            future.complete(view);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("Failed to open a gui for [" + player.getName() + "]: " + e);
            e.printStackTrace();
            future.completeExceptionally(e);
        }
    }

    private static void refuse(Gui gui, Player player, CompletableFuture<GuiView> future, String why) {
        String message = "The gui [" + gui.getTitle() + "] did not open for [" + player.getName() + "]: " + why + ".";
        EverNifeCore.getLog().warning(message);
        future.completeExceptionally(new IllegalStateException(message));
    }

    /** Opens {@code inventory} and answers whether the server confirmed it through its own event. */
    private static boolean attemptOpen(Player player, Inventory inventory) {
        PendingOpen previous = pending;
        PendingOpen attempt = new PendingOpen(player.getUniqueId(), inventory);
        pending = attempt;
        try {
            player.openInventory(inventory);
        } finally {
            pending = previous;
        }
        return attempt.confirmed;
    }

    /**
     * Replaces a view's container with one carrying {@code title}, which is the only way to rename a
     * window without NMS. The view itself survives - state, tasks and subscriptions all stay - and
     * the close the replacement causes is not reported as a close.
     *
     * @return whether the replacement window actually opened; on a refusal the view keeps the old one
     */
    static boolean swapSurfaceForTitle(GuiView view, String title) {
        Player player = view.getViewer();
        if (player == null || !player.isOnline()) {
            return false;
        }

        Inventory inventory = createInventory(view.getGui(), trimTitle(title));
        view.beginSurfaceSwap();
        try {
            if (!attemptOpen(player, inventory)) {
                EverNifeCore.getLog().warning("The gui of [" + player.getName() + "] could not be reopened "
                        + "to change its title; it keeps the previous one.");
                return false;
            }
            view.adoptSurface(new BukkitGuiSurface(inventory));
            return true;
        } finally {
            view.endSurfaceSwap();
        }
    }

    private static Inventory createInventory(Gui gui, String title) {
        GuiType type = gui.getType();
        if (type.isChest()) {
            return Bukkit.createInventory(null, type.sizeOf(gui.getRows()), title);
        }
        InventoryType inventoryType;
        try {
            inventoryType = InventoryType.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("This server has no inventory type named [" + type.name()
                    + "]. Use GuiType.CHEST, which every version has.", e);
        }
        return Bukkit.createInventory(null, inventoryType, title);
    }

    private static String trimTitle(String title) {
        if (title.length() > LEGACY_TITLE_LIMIT && MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3)) {
            return title.substring(0, LEGACY_TITLE_LIMIT);
        }
        return title;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Called by GuiListener
    // -----------------------------------------------------------------------------------------------------------------

    /** Confirms the open in flight. Anything else that opens a window is not ours. */
    static void confirmOpen(UUID viewerId, Inventory inventory) {
        if (pending != null && pending.viewerId.equals(viewerId) && pending.inventory == inventory) {
            pending.confirmed = true;
        }
    }

    static void handleClose(Player player, Inventory inventory) {
        GuiView view = OPEN.get(player.getUniqueId());
        if (view == null || !view.isSurface(inventory) || view.isSwappingSurface()) {
            return;
        }
        OPEN.remove(player.getUniqueId());
        view.release(CloseReason.PLAYER_CLOSED);
    }

    static void handleRelease(Player player, CloseReason reason) {
        GuiView view = OPEN.remove(player.getUniqueId());
        if (view != null) {
            view.release(reason);
        }
    }

    /**
     * Closes every open screen. Runs before the framework's listeners are unregistered, so each
     * screen still gets its {@code onClose} - otherwise a shutdown with an editable screen open
     * would turn its contents into free loot.
     */
    public static void closeAll() {
        if (OPEN.isEmpty()) {
            return;
        }
        List<GuiView> views = new ArrayList<>(OPEN.values());
        OPEN.clear();
        for (GuiView view : views) {
            Player player = view.getViewer();
            try {
                view.release(CloseReason.SHUTDOWN);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }

    private static final class PendingOpen {

        private final UUID viewerId;
        private final Inventory inventory;
        private boolean confirmed = false;

        private PendingOpen(UUID viewerId, Inventory inventory) {
            this.viewerId = viewerId;
            this.inventory = inventory;
        }

    }

}
