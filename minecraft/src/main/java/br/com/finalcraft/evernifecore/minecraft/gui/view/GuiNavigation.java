package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.nav.NavResult;
import br.com.finalcraft.evernifecore.minecraft.gui.nav.NavStack;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The chain of screens a player walked into, and the only thing that moves them along it.
 *
 * <p>A chain holds {@link GuiView}s, not {@code Gui}s, and that is the whole point: going back does
 * not build the previous screen again, it gives the player back the very view they left, with the page
 * it was on, the filter it had and every state its components remembered. Only the top of the chain
 * owns a window; everything under it is {@link GuiView#isSuspended() suspended} - alive, holding its
 * state, writing nowhere.</p>
 *
 * <p>Opening a screen that nobody navigated to - {@code gui.open(player)} - discards the chain, and so
 * does closing the window or leaving. A screen the player can no longer see never stays behind holding
 * tasks and a {@code Player}.</p>
 */
public final class GuiNavigation {

    private static final Map<UUID, NavStack<GuiView>> CHAINS = new LinkedHashMap<>();

    private GuiNavigation() {

    }

    /** Starts a chain at {@code view}, which is what an open outside any chain produces. */
    static void root(GuiView view) {
        NavStack<GuiView> chain = new NavStack<>();
        chain.push(view);
        CHAINS.put(view.getViewerId(), chain);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The three moves
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens {@code next} on top of {@code current}, which is set aside untouched.
     *
     * @return the value {@code next} hands back through {@code back(value)}. It is cancelled instead
     *         when the player abandons the screen without answering, so a {@code thenAccept} never
     *         runs on an answer nobody gave.
     */
    @Nonnull
    public static CompletableFuture<Object> open(@Nonnull GuiView current, @Nonnull Gui<?> next) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        GuiViews.onMainThread(() -> openOnMainThread(current, next, result));
        return result;
    }

    private static void openOnMainThread(GuiView current, Gui<?> next, CompletableFuture<Object> result) {
        Player player = current.getViewer();
        if (!suspend(current)) {
            result.completeExceptionally(new IllegalStateException("A screen can only open another one "
                    + "while it is the screen the player is looking at. This one is already closed, or "
                    + "something else took the window from it."));
            return;
        }

        GuiView opened = openedOrNull(GuiViews.openWithinChain(next, player));
        if (opened == null) {
            //the window was refused: the player is left where they were, if that can still be arranged
            if (!resume(current)) {
                discard(player.getUniqueId());
            }
            result.completeExceptionally(new IllegalStateException("The screen opened on top of ["
                    + current.getGui().getTitle() + "] was refused by the server, so nothing was pushed."));
            return;
        }

        opened.setPendingResult(result);
        chainOf(player.getUniqueId()).push(opened);
    }

    /**
     * Drops {@code current} and gives the player back the screen underneath, with everything it held.
     * At the bottom of a chain there is nothing to reveal, so the screen simply closes.
     *
     * @param value what the screen underneath's opener was waiting for, or {@code null}
     */
    public static void back(@Nonnull GuiView current, @Nullable Object value) {
        GuiViews.onMainThread(() -> backOnMainThread(current, value));
    }

    private static void backOnMainThread(GuiView current, Object value) {
        Player player = current.getViewer();
        NavStack<GuiView> chain = player == null ? null : CHAINS.get(player.getUniqueId());
        if (chain == null || chain.peek() != current) {
            //nothing to walk back through: closing is the whole of "leave this screen"
            current.closeNextTick();
            return;
        }

        NavResult<GuiView> revealed = chain.popWith(value);
        GuiViews.forget(current);
        current.release(CloseReason.REQUESTED);

        if (!revealed.hasScreen()) {
            CHAINS.remove(player.getUniqueId());
            player.closeInventory();
        } else if (!resume(revealed.getScreen())) {
            discard(player.getUniqueId());
        }

        answer(current, revealed.getValue());
    }

    /**
     * Puts {@code next} where {@code current} was: the screen underneath is untouched, so a later
     * {@code back()} goes to it and not to the screen just replaced.
     *
     * <p>The replacement inherits what {@code current} owed: whoever opened this step of the chain is
     * still waiting, and it is {@code next} that will answer them.</p>
     */
    public static void replace(@Nonnull GuiView current, @Nonnull Gui<?> next) {
        GuiViews.onMainThread(() -> replaceOnMainThread(current, next));
    }

    private static void replaceOnMainThread(GuiView current, Gui<?> next) {
        Player player = current.getViewer();
        if (player == null || current.isClosed()) {
            return;
        }
        NavStack<GuiView> chain = CHAINS.get(player.getUniqueId());
        CompletableFuture<Object> inherited = current.getPendingResult();
        if (chain != null && chain.peek() == current) {
            chain.pop();
        }
        GuiViews.forget(current);
        current.setPendingResult(null);
        current.release(CloseReason.REQUESTED);

        GuiView opened = openedOrNull(GuiViews.openWithinChain(next, player));
        if (opened == null) {
            discard(player.getUniqueId());
            cancel(inherited);
            return;
        }
        opened.setPendingResult(inherited);
        if (chain != null) {
            chain.push(opened);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Setting a screen aside and picking it up again - also what a chat prompt does
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Sets {@code view} aside: its window goes away, everything else it holds stays. Only the screen
     * the player is actually looking at can be suspended.
     *
     * @return whether it was suspended, which is {@code false} for a screen that is closed, already
     *         suspended, or no longer the one on screen
     */
    public static boolean suspend(@Nonnull GuiView view) {
        Player player = view.getViewer();
        if (player == null || view.isClosed() || view.isSuspended()) {
            return false;
        }
        if (GuiViews.getOpenView(player) != view) {
            return false;
        }
        if (!CHAINS.containsKey(view.getViewerId())) {
            root(view);
        }
        GuiViews.forget(view);
        view.suspend();
        return true;
    }

    /**
     * Gives {@code view} a window again and draws it whole, since the container it gets has never been
     * written into.
     *
     * @return whether the player is looking at it again; {@code false} when the screen is gone or the
     *         server refused the window
     */
    public static boolean resume(@Nonnull GuiView view) {
        Player player = view.getViewer();
        if (player == null || !player.isOnline() || view.isClosed() || !view.isSuspended()) {
            return false;
        }
        Gui<?> gui = view.getGui();
        String title = gui.getTitleFor(player);
        Inventory inventory = GuiViews.createInventory(gui, GuiViews.trimTitle(title));
        if (!GuiViews.attemptOpen(player, inventory)) {
            EverNifeCore.getLog().warning("The screen [" + title + "] could not be given back to ["
                    + player.getName() + "]; the server refused to open the window.");
            return false;
        }
        view.resume(new BukkitGuiSurface(inventory), title);
        GuiViews.remember(view);
        view.render();
        view.commitNow();
        return true;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Tearing a chain down
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Forgets the chain of one player, tearing down every screen in it that has no window of its own.
     * The screen the player is looking at is left to whatever is closing it.
     */
    public static void discard(@Nonnull UUID viewerId) {
        NavStack<GuiView> chain = CHAINS.remove(viewerId);
        if (chain == null) {
            return;
        }
        while (!chain.isEmpty()) {
            GuiView frame = chain.peek();
            chain.pop();
            cancel(frame.getPendingResult());
            frame.setPendingResult(null);
            if (frame.isSuspended()) {
                frame.release(CloseReason.REQUESTED);
            }
        }
    }

    /** {@link #discard(UUID)} for every player. The framework shutting down is what asks for it. */
    static void discardAll() {
        for (UUID viewerId : new ArrayList<UUID>(CHAINS.keySet())) {
            discard(viewerId);
        }
    }

    private static NavStack<GuiView> chainOf(UUID viewerId) {
        NavStack<GuiView> chain = CHAINS.get(viewerId);
        if (chain == null) {
            chain = new NavStack<>();
            CHAINS.put(viewerId, chain);
        }
        return chain;
    }

    /** Hands {@code value} to whoever opened {@code view}, on the main thread, exactly once. */
    private static void answer(GuiView view, Object value) {
        CompletableFuture<Object> pending = view.getPendingResult();
        view.setPendingResult(null);
        if (pending != null) {
            pending.complete(value);
        }
    }

    private static void cancel(CompletableFuture<Object> pending) {
        if (pending != null) {
            pending.cancel(false);
        }
    }

    /** The view an open produced, or {@code null} when the server refused the window. */
    private static GuiView openedOrNull(CompletableFuture<GuiView> opening) {
        return opening.isCompletedExceptionally() ? null : opening.getNow(null);
    }

}
