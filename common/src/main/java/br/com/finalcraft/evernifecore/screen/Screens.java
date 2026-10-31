package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every open {@link ScreenView}, the renderers that draw them, and the only way a view comes into being.
 *
 * <p>Renderers live in the {@code renderer} {@link TypeRegistry} - one mechanism for every kind of type, with its
 * collision rule and its per-owner cleanup. Per open, the highest priority renderer that {@link ScreenRenderer#supports
 * supports} the viewer and covers the screen's {@link Screen#getRequiredCapabilities() capabilities} draws it.</p>
 *
 * <p>A view is registered when its renderer has opened the window, never when {@code open} was called: a refused open
 * leaves nothing behind - no registration, no task, no retained player.</p>
 */
public final class Screens {

    private static final TypeRegistry<ScreenRenderer> RENDERERS = TypeRegistry.of(ScreenRenderer.class, "renderer");
    //the registry keeps descriptors; the instance each one answers for is kept here, read only through a live descriptor
    private static final Map<String, ScreenRenderer> INSTANCES = new ConcurrentHashMap<>();
    private static final Set<String> REFUSALS_LOGGED = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, ScreenView> OPEN = new LinkedHashMap<>();

    private Screens() {

    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Renderers
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Registers {@code renderer} under {@code owner}, the plugin its id names. Any thread.
     *
     * @throws IllegalStateException when another renderer already has that id, naming both owners
     * @throws IllegalArgumentException when the id names another plugin
     */
    public static void registerRenderer(@Nonnull ECPluginData owner, @Nonnull ScreenRenderer renderer) {
        RENDERERS.register(owner, TypeDescriptor.<ScreenRenderer>of(renderer.getId())
                .describe("Draws screens; priority " + renderer.getPriority() + ", capabilities " + renderer.getCapabilities() + ".")
                .factory(params -> renderer)
                .build());
        INSTANCES.put(renderer.getId(), renderer);
    }

    /** Takes out every renderer {@code owner} registered. Its open views are closed first. Renderer thread. */
    public static void unregisterRenderers(@Nonnull ECPluginData owner) {
        closeOwnedBy(owner);
        RENDERERS.unregisterAll(owner);
    }

    /** The renderers registered right now, highest priority first. */
    @Nonnull
    public static List<ScreenRenderer> getRenderers() {
        List<ScreenRenderer> renderers = new ArrayList<>();
        for (TypeDescriptor<ScreenRenderer> descriptor : RENDERERS.getAll()) {
            ScreenRenderer renderer = INSTANCES.get(descriptor.getKey());
            if (renderer != null) {
                renderers.add(renderer);
            }
        }
        renderers.sort((left, right) -> Integer.compare(right.getPriority(), left.getPriority()));
        return renderers;
    }

    @Nonnull
    public static TypeRegistry<ScreenRenderer> registry() {
        return RENDERERS;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Opening
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens {@code screen} for {@code viewer}, {@code args} being what {@code {0}}..{@code {n}} read in its text. Starts
     * a new navigation chain. Any thread: the work hops to the chosen renderer's thread.
     *
     * <p>The future completes once the renderer has the window up, and completes exceptionally - with a text that says
     * why and what to do - when no renderer can draw it, or the platform refused the window. The refusal is logged
     * either way, so ignoring the future is fine.</p>
     */
    @Nonnull
    public static CompletableFuture<ScreenView> open(@Nonnull FPlayer viewer, @Nonnull Screen<?> screen, @Nonnull Object... args) {
        CompletableFuture<ScreenView> future = new CompletableFuture<>();
        Screen<?> chosenScreen;
        ScreenRenderer renderer;
        try {
            Pick choice = choose(viewer, screen);
            chosenScreen = choice.screen;
            renderer = choice.renderer;
        } catch (IllegalStateException refused) {
            future.completeExceptionally(refused);
            return future;
        }
        renderer.getScheduler().run(() -> {
            //an open nobody navigated to starts a journey rather than continuing one
            ScreenNavigation.discard(viewer.getUniqueId());
            openOnRenderThread(chosenScreen, viewer, renderer, args, future, true);
        });
        return future;
    }

    /** Opens a screen inside the chain the viewer is already in, on the caller's (renderer) thread. */
    @Nonnull
    static CompletableFuture<ScreenView> openWithinChain(Screen<?> screen, FPlayer viewer, Object[] args) {
        CompletableFuture<ScreenView> future = new CompletableFuture<>();
        try {
            Pick choice = choose(viewer, screen);
            openOnRenderThread(choice.screen, viewer, choice.renderer, args, future, false);
        } catch (IllegalStateException refused) {
            future.completeExceptionally(refused);
        }
        return future;
    }

    private static final class Pick {

        private final Screen<?> screen;
        private final ScreenRenderer renderer;

        private Pick(Screen<?> screen, ScreenRenderer renderer) {
            this.screen = screen;
            this.renderer = renderer;
        }

    }

    /** The renderer that draws {@code screen} for {@code viewer} - or the fallback screen's - or the refusal that teaches. */
    private static Pick choose(FPlayer viewer, Screen<?> screen) {
        List<ScreenRenderer> serving = new ArrayList<>();
        for (ScreenRenderer renderer : getRenderers()) {
            if (supportsSafely(renderer, viewer, screen)) {
                serving.add(renderer);
            }
        }
        if (serving.isEmpty()) {
            throw refuse(screen, "No renderer draws the screen [" + screen.describe() + "] for " + viewer.getName() + " on "
                    + platformName() + ": " + (getRenderers().isEmpty() ? "none is registered on this platform"
                    : "none of " + ids(getRenderers()) + " supports this player") + ". A platform registers its own with "
                    + "Screens.registerRenderer; until one does, a screen cannot open here.");
        }
        Set<RendererCapability> needed = screen.getRequiredCapabilities();
        for (ScreenRenderer renderer : serving) {
            if (renderer.getCapabilities().containsAll(needed)) {
                return new Pick(screen, renderer);
            }
        }
        Screen<?> alternative = screen.getOnMissingCapability().getAlternative();
        if (alternative != null) {
            return choose(viewer, alternative);
        }
        ScreenRenderer best = serving.get(0);
        Set<RendererCapability> missing = EnumSet.copyOf(needed);
        missing.removeAll(best.getCapabilities());
        String message = "The screen [" + screen.describe() + "] needs " + join(missing) + ", and the renderer that serves "
                + viewer.getName() + " ([" + best.getId() + "], priority " + best.getPriority() + ") does not have it; "
                + (serving.size() == 1 ? "no other renderer serves this player" : "neither do " + ids(serving.subList(1, serving.size())))
                + ". Drop what needs it, or declare onMissingCapability(ScreenFallback.open(screen)) with a screen that "
                + "needs none.";
        if (REFUSALS_LOGGED.add(screen.describe() + "|" + best.getId())) {
            EverNifeCore.getLog().warning("{}", message);
        }
        throw new IllegalStateException(message);
    }

    private static IllegalStateException refuse(Screen<?> screen, String message) {
        if (REFUSALS_LOGGED.add(screen.describe() + "|<none>")) {
            EverNifeCore.getLog().warning("{}", message);
        }
        return new IllegalStateException(message);
    }

    private static boolean supportsSafely(ScreenRenderer renderer, FPlayer viewer, Screen<?> screen) {
        try {
            return renderer.supports(viewer, screen);
        } catch (Throwable broken) {
            EverNifeCore.getLog().severe("The renderer [{}] threw while asked whether it supports {}; it is skipped.",
                    renderer.getId(), viewer.getName(), broken);
            return false;
        }
    }

    private static String platformName() {
        try {
            return EverNifeCore.getPlatform().getClass().getSimpleName();
        } catch (Throwable none) {
            return "this platform";
        }
    }

    private static String ids(List<ScreenRenderer> renderers) {
        List<String> ids = new ArrayList<>();
        for (ScreenRenderer renderer : renderers) {
            ids.add("[" + renderer.getId() + "]");
        }
        return String.join(", ", ids);
    }

    private static String join(Set<RendererCapability> capabilities) {
        List<String> names = new ArrayList<>();
        for (RendererCapability capability : capabilities) {
            names.add(capability.name());
        }
        return String.join(" and ", names);
    }

    private static void openOnRenderThread(Screen<?> screen, FPlayer viewer, ScreenRenderer renderer, Object[] args,
                                           CompletableFuture<ScreenView> future, boolean root) {
        ScreenView opening = null;
        try {
            if (!viewer.isOnline()) {
                refuseOpen(screen, viewer, future, "the player is no longer online");
                return;
            }
            ScreenView view = new ScreenView(screen, viewer, renderer, args);
            ScreenSurface surface = renderer.open(view);
            if (surface == null) {
                refuseOpen(screen, viewer, future, "the renderer [" + renderer.getId() + "] did not open the window "
                        + "(the player may be leaving, or another plugin cancelled the open)");
                return;
            }
            view.attach(surface);
            opening = view;

            //the window this one replaced should have reported its close already; one that vanished without a word would
            //otherwise stay here forever, holding a player and its tasks alive
            ScreenView displaced;
            synchronized (OPEN) {
                displaced = OPEN.put(viewer.getUniqueId(), view);
            }
            if (displaced != null) {
                displaced.release(CloseReason.REQUESTED);
            }
            if (root) {
                ScreenNavigation.root(view);
            }
            view.start();
            view.render();
            view.commitNow();
            future.complete(view);
            ECEventBus.global().postIfListened(ScreenOpenEvent.class, () -> new ScreenOpenEvent(view));
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("Failed to open the screen [{}] for [{}]", screen.describe(), viewer.getName(), e);
            //a half-built screen is worse than none: take it back before anybody clicks a skeleton
            if (opening != null) {
                synchronized (OPEN) {
                    OPEN.remove(viewer.getUniqueId(), opening);
                }
                if (root) {
                    ScreenNavigation.discard(viewer.getUniqueId());
                }
                ScreenSurface surface = opening.getSurface();
                opening.release(CloseReason.REQUESTED);
                if (surface != null) {
                    surface.close(CloseReason.REQUESTED);
                }
            }
            future.completeExceptionally(e);
        }
    }

    private static void refuseOpen(Screen<?> screen, FPlayer viewer, CompletableFuture<ScreenView> future, String why) {
        String message = "The screen [" + screen.describe() + "] did not open for [" + viewer.getName() + "]: " + why + ".";
        //a title or a name carrying '{}' must not be read as a placeholder, so it travels as a parameter
        EverNifeCore.getLog().warning("{}", message);
        future.completeExceptionally(new IllegalStateException(message));
    }

    /** Gives a suspended view a window again, from the renderer that drew it. Renderer thread. */
    static boolean reopen(ScreenView view) {
        FPlayer viewer = view.getViewer();
        if (viewer == null || !viewer.isOnline() || view.isClosed() || !view.isSuspended()) {
            return false;
        }
        String title = view.resolveTitle();
        ScreenSurface surface = view.getRenderer().open(view);
        if (surface == null) {
            EverNifeCore.getLog().warning("The screen [" + title + "] could not be given back to [" + viewer.getName()
                    + "]; the renderer refused to open the window.");
            return false;
        }
        view.resume(surface);
        remember(view);
        view.render();
        view.commitNow();
        return true;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    /** The screen {@code viewer} has open right now, or {@code null}. Any thread. */
    @Nullable
    public static ScreenView getOpenView(@Nullable FPlayer viewer) {
        return viewer == null ? null : getOpenView(viewer.getUniqueId());
    }

    @Nullable
    public static ScreenView getOpenView(@Nonnull UUID viewerId) {
        synchronized (OPEN) {
            return OPEN.get(viewerId);
        }
    }

    public static int getOpenCount() {
        synchronized (OPEN) {
            return OPEN.size();
        }
    }

    /** Re-renders every open screen; a pass writes only what changed. Any thread. */
    public static int refreshAll() {
        List<ScreenView> views = snapshot();
        for (ScreenView view : views) {
            view.refresh();
        }
        return views.size();
    }

    private static List<ScreenView> snapshot() {
        synchronized (OPEN) {
            return new ArrayList<>(OPEN.values());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Called by renderers
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The renderer's report that {@code view}'s window is gone - closed by the player, by a request, by a disconnect.
     * Releases the view and its navigation chain. A view set aside (its window replaced) is not affected. Renderer thread.
     */
    public static void windowClosed(@Nonnull ScreenView view, @Nonnull CloseReason reason) {
        boolean wasOpen;
        synchronized (OPEN) {
            wasOpen = OPEN.remove(view.getViewerId(), view);
        }
        if (!wasOpen) {
            return;
        }
        ScreenNavigation.discard(view.getViewerId());
        view.release(reason);
    }

    /** {@code viewer} left or changed world: whatever screen they had goes, with {@code reason}. Renderer thread. */
    public static void viewerGone(@Nonnull UUID viewerId, @Nonnull CloseReason reason) {
        ScreenView view;
        synchronized (OPEN) {
            view = OPEN.remove(viewerId);
        }
        ScreenNavigation.discard(viewerId);
        if (view != null) {
            view.release(reason);
        }
    }

    /** Drops {@code view} from the registry without tearing it down - it is being set aside. */
    static void forget(ScreenView view) {
        synchronized (OPEN) {
            OPEN.remove(view.getViewerId(), view);
        }
    }

    /** Makes {@code view} the screen a renderer routes {@code viewer}'s events to. */
    static void remember(ScreenView view) {
        synchronized (OPEN) {
            OPEN.put(view.getViewerId(), view);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Closing everything
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Closes every open screen ({@link CloseReason#SHUTDOWN}). Runs before the platform's listeners go, so each screen
     * still gets its {@code onClose} - a shutdown with an editable screen open would otherwise turn its contents into
     * free loot. Renderer thread.
     */
    public static void closeAll() {
        ScreenNavigation.discardAll();
        List<ScreenView> views;
        synchronized (OPEN) {
            views = new ArrayList<>(OPEN.values());
            OPEN.clear();
        }
        close(views, CloseReason.SHUTDOWN);
    }

    /** Closes every open screen whose content {@code owner} owns, or whose renderer it registered. Renderer thread. */
    public static void closeOwnedBy(@Nonnull ECPluginData owner) {
        List<ScreenView> owned = new ArrayList<>();
        synchronized (OPEN) {
            for (ScreenView view : OPEN.values()) {
                ECPluginData rendererOwner = RENDERERS.find(view.getRenderer().getId())
                        .flatMap(RENDERERS::getOwner).orElse(null);
                if (view.getScreen().getOwner() == owner || rendererOwner == owner) {
                    owned.add(view);
                }
            }
            for (ScreenView view : owned) {
                OPEN.remove(view.getViewerId(), view);
            }
        }
        for (ScreenView view : owned) {
            ScreenNavigation.discard(view.getViewerId());
        }
        close(owned, CloseReason.SHUTDOWN);
    }

    private static void close(List<ScreenView> views, CloseReason reason) {
        for (ScreenView view : views) {
            ScreenSurface surface = view.getSurface();
            try {
                view.release(reason);
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("A screen of [{}] failed to close; the screens after it are still being closed",
                        view.getViewerName(), e);
            }
            if (surface != null) {
                try {
                    surface.close(reason);
                } catch (Throwable e) {
                    EverNifeCore.getLog().severe("The window of [{}] failed to close", view.getViewerName(), e);
                }
            }
        }
    }

}
