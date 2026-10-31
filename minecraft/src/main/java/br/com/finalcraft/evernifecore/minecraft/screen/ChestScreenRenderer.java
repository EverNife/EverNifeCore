package br.com.finalcraft.evernifecore.minecraft.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.color.ColorEnum;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.screen.prompt.BukkitChatPromptChannel;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.screen.Grid;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenRenderer;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import br.com.finalcraft.evernifecore.screen.ScreenSurface;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.prompt.PromptChannel;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Bukkit fallback renderer: every screen is a vanilla container ({@link ChestKind}), every icon an
 * {@code ItemStack}. Priority 0, so any renderer a client mod registers wins for the players who have it. It is the
 * only renderer with {@link RendererCapability#STORAGE_SLOTS}.
 *
 * <p>A window counts as open once the server has <b>confirmed</b> it ({@code InventoryOpenEvent}), never when
 * {@code openInventory} was called: a refused open - the player is sleeping, leaving, another plugin cancelled it -
 * answers no surface. Titles longer than 32 characters are cut on 1.8 and older, where the window packet cannot carry
 * them and the server throws instead.</p>
 */
public final class ChestScreenRenderer implements ScreenRenderer {

    public static final String ID = "evernifecore:chest";

    private static final int LEGACY_TITLE_LIMIT = 32;

    /** The open in flight. {@code openInventory} fires its event before returning, so one slot is enough. */
    private static PendingOpen pending;

    /** The chest window of each view, which the view itself may know only through a wrapper. */
    private static final Map<ScreenView, BukkitScreenSurface> SURFACES = Collections.synchronizedMap(new IdentityHashMap<>());

    private final Set<RendererCapability> capabilities = Collections.unmodifiableSet(
            EnumSet.of(RendererCapability.STORAGE_SLOTS, RendererCapability.TITLE_CHANGE));

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Nonnull
    @Override
    public Set<RendererCapability> getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean supports(@Nonnull FPlayer viewer, @Nonnull Screen<?> screen) {
        Player player = playerOf(viewer);
        return player != null && player.isOnline() && ChestKind.of(screen.getGrid()) != null;
    }

    @Nullable
    @Override
    public ScreenSurface open(@Nonnull ScreenView view) {
        Player player = playerOf(view);
        if (player == null || !player.isOnline()) {
            return null;
        }
        Inventory opened = attemptOpen(player, createInventory(view.getGrid(), trimTitle(view.getCurrentTitle())));
        if (opened == null) {
            return null;
        }
        BukkitScreenSurface surface = new BukkitScreenSurface(this, view, opened);
        BukkitScreenSurface previous = SURFACES.put(view, surface);
        try {
            if (previous != null) {
                surface.carryStoragesFrom(previous);
            } else {
                surface.startStorages();
            }
        } catch (RuntimeException | Error broken) {
            //the window is already in front of the player with nothing in it: take it back before anybody clicks it
            SURFACES.remove(view);
            player.closeInventory();
            throw broken;
        }
        return surface;
    }

    @Nonnull
    @Override
    public ScreenScheduler getScheduler() {
        return BukkitScreenScheduler.INSTANCE;
    }

    @Nullable
    @Override
    public PromptChannel getPromptChannel() {
        return BukkitChatPromptChannel.get();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    /** The chest window {@code view} draws through right now, or {@code null} when another renderer drew it. */
    @Nullable
    public static BukkitScreenSurface surfaceOf(@Nullable ScreenView view) {
        return view == null ? null : SURFACES.get(view);
    }

    /** The online Bukkit player behind {@code viewer}, or {@code null}. */
    @Nullable
    public static Player playerOf(@Nullable FPlayer viewer) {
        if (viewer == null) {
            return null;
        }
        if (viewer instanceof MinecraftFPlayer) {
            Player player = ((MinecraftFPlayer<?>) viewer).getPlayer();
            if (player != null) {
                return player;
            }
        }
        return Bukkit.getPlayer(viewer.getUniqueId());
    }

    @Nullable
    static Player playerOf(@Nonnull ScreenView view) {
        return playerOf(view.getViewer());
    }

    void forget(BukkitScreenSurface surface) {
        SURFACES.remove(surface.getView(), surface);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Windows
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens {@code inventory} and answers the container the server ended up showing, or {@code null} when it refused.
     * The answer is rarely the object handed over: opening a chest makes the platform build its own container and hand
     * out a fresh wrapper over the same storage. Every click, close and render names THAT one.
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

    /** Confirms the open in flight, with the container it turned out to be. Anything else is not ours. */
    static void confirmOpen(UUID viewerId, Inventory inventory) {
        if (pending != null && pending.viewerId.equals(viewerId)) {
            //the last window opened inside the call is the one the player is left looking at
            pending.opened = inventory;
        }
    }

    /**
     * Replaces the window with one carrying {@code title}, which is the only way to rename a container without NMS. The
     * view survives - state, tasks, editable areas - and the close the replacement causes is not reported as a close.
     */
    void swapForTitle(BukkitScreenSurface surface, String title) {
        ScreenView view = surface.getView();
        Player player = playerOf(view);
        if (player == null || !player.isOnline()) {
            return;
        }
        surface.setSwapping(true);
        Inventory opened;
        try {
            opened = attemptOpen(player, createInventory(view.getGrid(), trimTitle(title)));
        } finally {
            surface.setSwapping(false);
        }
        if (opened == null) {
            EverNifeCore.getLog().warning("The screen of [" + view.getViewerName() + "] could not be reopened to change "
                    + "its title; it keeps the previous one.");
            return;
        }
        surface.moveInto(opened);
    }

    static Inventory createInventory(Grid grid, String title) {
        ChestKind kind = ChestKind.of(grid);
        if (kind == null) {
            throw new IllegalStateException("No container has a " + grid + ". The chest draws " + ChestKind.names()
                    + " - Screen.of(rows) or ChestKind.X.newScreen().");
        }
        if (kind.isChest()) {
            return Bukkit.createInventory(null, grid.getSize(), title);
        }
        InventoryType inventoryType;
        try {
            inventoryType = InventoryType.valueOf(kind.name());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("This server has no inventory type named [" + kind.name()
                    + "]. Use ChestKind.CHEST, which every version has.", e);
        }
        return Bukkit.createInventory(null, inventoryType, title);
    }

    static String trimTitle(String title) {
        if (title.length() <= LEGACY_TITLE_LIMIT || !MCVersion.isLowerEquals(MCDetailedVersion.v1_8_R3)) {
            return title;
        }
        //a colour code is two characters read together: cutting between them leaves a lone section sign
        int end = LEGACY_TITLE_LIMIT;
        if (title.charAt(end - 1) == ColorEnum.COLOR_CHAR) {
            end--;
        }
        return title.substring(0, end);
    }

    private static final class PendingOpen {

        private final UUID viewerId;
        private Inventory opened;

        private PendingOpen(UUID viewerId) {
            this.viewerId = viewerId;
        }

    }

}
