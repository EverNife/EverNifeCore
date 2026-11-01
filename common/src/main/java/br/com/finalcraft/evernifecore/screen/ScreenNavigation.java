package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The chain of screens a player walked into, and the only thing that moves them along it.
 *
 * <p>A chain holds {@link ScreenView}s, not {@code Screen}s: going back gives the player the very view they left, with
 * its page, its filter and every state its components remembered. Only the top of the chain has a window; everything
 * under it is {@link ScreenView#isSuspended() suspended}. Opening a screen nobody navigated to discards the chain, and
 * so do closing the window and leaving.</p>
 *
 * <p>Every move hops one tick on the view's renderer: a renderer applies the click against the window open when the
 * click ends, and by then it must still be the old one.</p>
 */
public final class ScreenNavigation {

    private static final Map<UUID, NavStack<ScreenView>> CHAINS = new LinkedHashMap<>();

    private ScreenNavigation() {

    }

    /** Starts a chain at {@code view}, which is what an open outside any chain produces. */
    static synchronized void root(ScreenView view) {
        NavStack<ScreenView> chain = new NavStack<>();
        chain.push(view);
        CHAINS.put(view.getViewerId(), chain);
    }

    /**
     * Opens {@code next} on top of {@code current}, which is set aside untouched. Completes with what {@code next} hands
     * back through {@code back(value)}; cancelled when the player abandons it without answering. Any thread.
     */
    @Nonnull
    public static CompletableFuture<Object> open(@Nonnull ScreenView current, @Nonnull Screen<?> next) {
        return open(current, next, null);
    }

    /** {@link #open(ScreenView, Screen)} with {@code args} as what {@code {0}}..{@code {n}} read on {@code next}; null keeps the current ones. */
    @Nonnull
    public static CompletableFuture<Object> open(@Nonnull ScreenView current, @Nonnull Screen<?> next, @Nullable Object[] args) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        current.getScheduler().later(1L, () -> openOnRenderThread(current, next, args, result));
        return result;
    }

    private static void openOnRenderThread(ScreenView current, Screen<?> next, @Nullable Object[] args, CompletableFuture<Object> result) {
        FPlayer viewer = current.getViewer();
        if (!suspend(current)) {
            result.completeExceptionally(new IllegalStateException("A screen can only open another one while it is the "
                    + "screen the player is looking at. This one is already closed, or something else took the window."));
            return;
        }
        ScreenView opened = openedOrNull(Screens.openWithinChain(next, viewer, args != null ? args : current.getArguments().toArray()));
        if (opened == null) {
            if (!resume(current)) {
                discard(viewer.getUniqueId());
            }
            result.completeExceptionally(new IllegalStateException("The screen opened on top of ["
                    + current.getScreen().describe() + "] was refused, so nothing was pushed."));
            return;
        }
        opened.setPendingResult(result);
        chainOf(viewer.getUniqueId()).push(opened);
    }

    /**
     * Drops {@code current} and gives the player back the screen underneath, with everything it held; at the bottom of a
     * chain the screen simply closes. {@code value} is what the opener of {@code current} waits for. Any thread.
     */
    public static void back(@Nonnull ScreenView current, @Nullable Object value) {
        current.getScheduler().later(1L, () -> backOnRenderThread(current, value));
    }

    private static void backOnRenderThread(ScreenView current, Object value) {
        FPlayer viewer = current.getViewer();
        NavStack<ScreenView> chain;
        synchronized (ScreenNavigation.class) {
            chain = viewer == null ? null : CHAINS.get(viewer.getUniqueId());
        }
        if (chain == null || chain.peek() != current) {
            current.closeNextTick();
            return;
        }
        NavResult<ScreenView> revealed = chain.popWith(value);
        ScreenSurface surface = current.getSurface();
        Screens.forget(current);
        current.release(CloseReason.REQUESTED);

        if (!revealed.hasScreen()) {
            synchronized (ScreenNavigation.class) {
                CHAINS.remove(viewer.getUniqueId());
            }
            if (surface != null) {
                surface.close(CloseReason.REQUESTED);
            }
        } else if (!resume(revealed.getScreen())) {
            discard(viewer.getUniqueId());
        }
        answer(current, revealed.getValue());
    }

    /**
     * Puts {@code next} where {@code current} was: the screen underneath is untouched, and {@code next} inherits what
     * {@code current} owed its opener. Any thread.
     */
    public static void replace(@Nonnull ScreenView current, @Nonnull Screen<?> next) {
        current.getScheduler().later(1L, () -> replaceOnRenderThread(current, next));
    }

    private static void replaceOnRenderThread(ScreenView current, Screen<?> next) {
        FPlayer viewer = current.getViewer();
        if (viewer == null || current.isClosed()) {
            return;
        }
        NavStack<ScreenView> chain;
        synchronized (ScreenNavigation.class) {
            chain = CHAINS.get(viewer.getUniqueId());
        }
        CompletableFuture<Object> inherited = current.getPendingResult();
        if (chain != null && chain.peek() == current) {
            chain.pop();
        }
        Screens.forget(current);
        current.setPendingResult(null);
        current.release(CloseReason.REQUESTED);

        ScreenView opened = openedOrNull(Screens.openWithinChain(next, viewer, current.getArguments().toArray()));
        if (opened == null) {
            discard(viewer.getUniqueId());
            cancel(inherited);
            return;
        }
        opened.setPendingResult(inherited);
        if (chain != null) {
            chain.push(opened);
        }
    }

    /**
     * Sets {@code view} aside: its window goes, everything else stays. Only the screen the player is looking at can be
     * suspended - also what {@code PromptRegistry} does before a channel asks. Renderer thread.
     *
     * @return whether it was suspended
     */
    public static boolean suspend(@Nonnull ScreenView view) {
        FPlayer viewer = view.getViewer();
        if (viewer == null || view.isClosed() || view.isSuspended() || Screens.getOpenView(viewer) != view) {
            return false;
        }
        synchronized (ScreenNavigation.class) {
            if (!CHAINS.containsKey(view.getViewerId())) {
                root(view);
            }
        }
        Screens.forget(view);
        view.suspend();
        return true;
    }

    /** Gives {@code view} a window again and draws it whole. Renderer thread. */
    public static boolean resume(@Nonnull ScreenView view) {
        return Screens.reopen(view);
    }

    /** Forgets one player's chain, tearing down every screen in it that has no window of its own. Renderer thread. */
    public static void discard(@Nonnull UUID viewerId) {
        for (ScreenView setAside : detach(viewerId)) {
            setAside.release(CloseReason.REQUESTED);
        }
    }

    /**
     * Forgets one player's chain, nobody left waiting on it, and hands back the screens in it that have no window of
     * their own - still to be released, on their renderer's thread. Any thread.
     */
    @Nonnull
    static List<ScreenView> detach(@Nonnull UUID viewerId) {
        NavStack<ScreenView> chain;
        synchronized (ScreenNavigation.class) {
            chain = CHAINS.remove(viewerId);
        }
        List<ScreenView> setAside = new ArrayList<>();
        if (chain == null) {
            return setAside;
        }
        while (!chain.isEmpty()) {
            ScreenView frame = chain.peek();
            chain.pop();
            cancel(frame.getPendingResult());
            frame.setPendingResult(null);
            if (frame.isSuspended()) {
                setAside.add(frame);
            }
        }
        return setAside;
    }

    /** {@link #detach(UUID)} for every player - the framework shutting down asks for it. Any thread. */
    @Nonnull
    static List<ScreenView> detachAll() {
        ArrayList<UUID> viewers;
        synchronized (ScreenNavigation.class) {
            viewers = new ArrayList<>(CHAINS.keySet());
        }
        List<ScreenView> setAside = new ArrayList<>();
        for (UUID viewerId : viewers) {
            setAside.addAll(detach(viewerId));
        }
        return setAside;
    }

    private static synchronized NavStack<ScreenView> chainOf(UUID viewerId) {
        NavStack<ScreenView> chain = CHAINS.get(viewerId);
        if (chain == null) {
            chain = new NavStack<>();
            CHAINS.put(viewerId, chain);
        }
        return chain;
    }

    private static void answer(ScreenView view, Object value) {
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

    private static ScreenView openedOrNull(CompletableFuture<ScreenView> opening) {
        return opening.isCompletedExceptionally() ? null : opening.getNow(null);
    }

}
