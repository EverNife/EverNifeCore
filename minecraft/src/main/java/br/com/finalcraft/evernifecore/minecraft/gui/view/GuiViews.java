package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.color.ColorEnum;
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

    /**
     * Re-renders every screen that is open right now.
     *
     * <p>Nothing is written twice for it: a render pass commits only the slots whose rendered item
     * changed, so a screen the reload did not touch costs one comparison and no packet.</p>
     *
     * @return how many screens were re-rendered
     */
    public static int refreshAll() {
        List<GuiView> views = new ArrayList<>(OPEN.values());
        for (GuiView view : views) {
            view.refresh();
        }
        return views.size();
    }

    /** See {@link Gui#open(Player)}. */
    @Nonnull
    public static CompletableFuture<GuiView> open(@Nonnull Gui<?> gui, @Nonnull Player player) {
        CompletableFuture<GuiView> future = new CompletableFuture<>();
        onMainThread(() -> {
            //an open nobody navigated to starts a journey rather than continuing one
            GuiNavigation.discard(player.getUniqueId());
            openOnMainThread(gui, player, future, true);
        });
        return future;
    }

    /** Opens a screen inside the chain the player is already in - see {@link GuiNavigation}. */
    @Nonnull
    static CompletableFuture<GuiView> openWithinChain(Gui<?> gui, Player player) {
        CompletableFuture<GuiView> future = new CompletableFuture<>();
        openOnMainThread(gui, player, future, false);
        return future;
    }

    /**
     * Runs {@code task} on the main thread, right now when that is already where we are.
     *
     * <p>The hop everything that touches a screen from somewhere else has to take: a chat answer, a
     * timeout, a database callback.</p>
     */
    public static void onMainThread(@Nonnull Runnable task) {
        if (FCBukkitUtil.isMainThread()) {
            task.run();
        } else {
            McFCScheduler.INSTANCE.runSync(task);
        }
    }

    private static void openOnMainThread(Gui<?> gui, Player player, CompletableFuture<GuiView> future,
                                         boolean root) {
        GuiView opening = null;
        try {
            if (!player.isOnline()) {
                refuse(gui, player, future, "the player is no longer online");
                return;
            }

            String title = gui.getTitleFor(player);
            Inventory opened = attemptOpen(player, createInventory(gui, trimTitle(title)));
            if (opened == null) {
                refuse(gui, player, future, "the server did not open the window "
                        + "(the player may be sleeping or leaving, or another plugin cancelled the open)");
                return;
            }

            GuiView view = new GuiView(gui, player, new BukkitGuiSurface(opened),
                    BukkitGuiScheduler.INSTANCE, title);
            opening = view;

            //almost never fires: openInventory closes the previous window before returning, so its
            //InventoryCloseEvent already went through handleClose (as PLAYER_CLOSED) by the time this
            //put runs. What is left here is the view whose window vanished without that event - the
            //entry would otherwise stay in OPEN forever, holding a Player and its tasks alive
            GuiView displaced = OPEN.put(player.getUniqueId(), view);
            if (displaced != null) {
                displaced.release(CloseReason.REQUESTED);
            }

            if (root) {
                GuiNavigation.root(view);
            }
            view.start();
            view.render();
            view.commitNow();
            future.complete(view);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("Failed to open a gui for [{}]", player.getName(), e);
            //a half-built screen is worse than none: the window is already in front of the player, and
            //nothing has rendered into it. Take it back before anybody sees a skeleton they can click
            if (opening != null) {
                OPEN.remove(player.getUniqueId(), opening);
                if (root) {
                    //the chain started at this very view, so nothing under it is being thrown away
                    GuiNavigation.discard(player.getUniqueId());
                }
                opening.release(CloseReason.REQUESTED);
                player.closeInventory();
            }
            future.completeExceptionally(e);
        }
    }

    private static void refuse(Gui<?> gui, Player player, CompletableFuture<GuiView> future, String why) {
        String message = "The gui [" + gui.getTitle() + "] did not open for [" + player.getName() + "]: " + why + ".";
        //one text for the console and for the exception; a gui title or a player name carrying '{}' must
        //not be read as a placeholder, so it travels as a parameter
        EverNifeCore.getLog().warning("{}", message);
        future.completeExceptionally(new IllegalStateException(message));
    }

    /**
     * Opens {@code inventory} and answers the container the server ended up showing, or {@code null}
     * when it refused to show one.
     *
     * <p>The answer is rarely the object handed over: opening a chest makes the platform build its own
     * container and hand out a fresh wrapper over the same storage. Everything that follows - a click,
     * a close, a render - names THAT one, so it is the one a view is drawn on.</p>
     */
    @Nullable
    static Inventory attemptOpen(Player player, Inventory inventory) {
        PendingOpen previous = pending;
        PendingOpen attempt = new PendingOpen(player.getUniqueId());
        pending = attempt;
        try {
            player.openInventory(inventory);
        } finally {
            pending = previous;
        }
        return attempt.opened;
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
            Inventory opened = attemptOpen(player, inventory);
            if (opened == null) {
                EverNifeCore.getLog().warning("The gui of [" + player.getName() + "] could not be reopened "
                        + "to change its title; it keeps the previous one.");
                return false;
            }
            view.adoptSurface(new BukkitGuiSurface(opened));
            return true;
        } finally {
            view.endSurfaceSwap();
        }
    }

    static Inventory createInventory(Gui<?> gui, String title) {
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

    static String trimTitle(String title) {
        if (title.length() <= LEGACY_TITLE_LIMIT || !MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3)) {
            return title;
        }
        //a colour code is two characters and the client reads them together: cutting between them
        //would leave a lone section sign, which is drawn as a glyph instead of colouring anything
        int end = LEGACY_TITLE_LIMIT;
        if (title.charAt(end - 1) == ColorEnum.COLOR_CHAR) {
            end--;
        }
        return title.substring(0, end);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Called by GuiListener
    // -----------------------------------------------------------------------------------------------------------------

    /** Confirms the open in flight, with the container it turned out to be. Anything else is not ours. */
    static void confirmOpen(UUID viewerId, Inventory inventory) {
        if (pending != null && pending.viewerId.equals(viewerId)) {
            //the last window opened inside the call is the one the player is left looking at
            pending.opened = inventory;
        }
    }

    static void handleClose(Player player, Inventory inventory) {
        GuiView view = OPEN.get(player.getUniqueId());
        if (view == null || !view.isSurface(inventory) || view.isSwappingSurface()) {
            return;
        }
        OPEN.remove(player.getUniqueId());
        GuiNavigation.discard(player.getUniqueId());
        view.release(CloseReason.PLAYER_CLOSED);
    }

    static void handleRelease(Player player, CloseReason reason) {
        GuiView view = OPEN.remove(player.getUniqueId());
        GuiNavigation.discard(player.getUniqueId());
        if (view != null) {
            view.release(reason);
        }
    }

    /** Drops {@code view} from the registry without tearing it down - it is being set aside. */
    static void forget(GuiView view) {
        OPEN.remove(view.getViewerId(), view);
    }

    /** Makes {@code view} the screen the listener routes {@code viewer}'s events to. */
    static void remember(GuiView view) {
        OPEN.put(view.getViewerId(), view);
    }

    /**
     * Closes every open screen. Runs before the framework's listeners are unregistered, so each
     * screen still gets its {@code onClose} - otherwise a shutdown with an editable screen open
     * would turn its contents into free loot.
     */
    public static void closeAll() {
        GuiNavigation.discardAll();
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
                EverNifeCore.getLog().severe("A gui of [{}] failed to close during "
                        + "shutdown; the screens after it are still being closed", view.getViewerName(), e);
            }
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }

    private static final class PendingOpen {

        private final UUID viewerId;
        private Inventory opened;

        private PendingOpen(UUID viewerId) {
            this.viewerId = viewerId;
        }

    }

}
