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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
    private static final Map<ScreenKey, Map<ScreenScope, Registered>> OVERRIDES = new LinkedHashMap<>();
    private static final int MAX_FALLBACKS = 8;

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
            Pick choice = choose(viewer, screen, args, 0);
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

    /**
     * Opens whatever answers {@code key} for {@code viewer}, resolving four layers, most specific first: (1) the key the
     * caller asked for instead ({@link ScreenRequest#withScreen}); (2) the override of the content owner
     * ({@link ScreenScope#ownedBy}, matched on {@link ScreenRequest#getOwner()}); (3) the server override; (4) the core's
     * default. The factory runs on the calling thread; the rest is {@link #open(FPlayer, Screen, Object...)}.
     *
     * <p>The future fails, with a text that says what to register, when no layer answers the key.</p>
     */
    @Nonnull
    public static CompletableFuture<ScreenView> open(@Nonnull FPlayer viewer, @Nonnull ScreenKey key, @Nonnull ScreenRequest request) {
        Screen<?> screen;
        try {
            screen = build(viewer, key, request);
        } catch (RuntimeException refused) {
            CompletableFuture<ScreenView> failed = new CompletableFuture<>();
            failed.completeExceptionally(refused);
            return failed;
        }
        return open(viewer, screen, request.getArguments().toArray());
    }

    /**
     * Asks {@code viewer} {@code request} on whatever answers {@link ScreenKey#CONFIRM}: {@code true} on confirm,
     * {@code false} on deny, cancelled when the window goes without an answer. Any thread.
     */
    @Nonnull
    public static CompletableFuture<Boolean> confirm(@Nonnull FPlayer viewer, @Nonnull ConfirmRequest request) {
        return answerOf(open(viewer, ScreenKey.CONFIRM, ScreenRequest.of(request.getOwner()).payload(request)), Boolean.class);
    }

    /**
     * What {@code opened}'s screen hands back through {@code back(value)} - the answer of a screen opened outside any
     * chain. Cancelled when the window goes without an answer.
     */
    @Nonnull
    static <R> CompletableFuture<R> answerOf(@Nonnull CompletableFuture<ScreenView> opened, @Nonnull Class<R> type) {
        CompletableFuture<Object> answer = new CompletableFuture<>();
        opened.whenComplete((view, refused) -> {
            if (refused != null) {
                answer.completeExceptionally(refused);
            } else {
                //open completes on the renderer thread before any click is read: no answer can come before this
                view.setPendingResult(answer);
            }
        });
        return typed(answer, type, "its opener");
    }

    /**
     * {@code answer} as {@code type}: a value of another type fails naming {@code waiter}, and a cancelled answer stays
     * CANCELLED - {@code thenApply} would turn it into a plain failure, and "nobody answered" into an error.
     */
    @Nonnull
    static <R> CompletableFuture<R> typed(@Nonnull CompletableFuture<Object> answer, @Nonnull Class<R> type, @Nonnull String waiter) {
        CompletableFuture<R> typed = new CompletableFuture<>();
        answer.whenComplete((value, failed) -> {
            if (failed != null) {
                typed.completeExceptionally(failed); //a CancellationException as is: still isCancelled()
            } else if (value != null && !type.isInstance(value)) {
                typed.completeExceptionally(new ClassCastException("The screen answered a " + value.getClass().getSimpleName()
                        + ", and " + waiter + " waits for a " + type.getSimpleName() + ". Hand back(...) a " + type.getSimpleName()
                        + ", or open it without a type."));
            } else {
                typed.complete(type.cast(value));
            }
        });
        return typed;
    }

    /**
     * Lets {@code plugin} answer {@code key} within {@code scope}. {@link Cancellable#cancel()} takes it back, and the
     * layer below answers again; the plugin's shutdown takes it back too. Any thread.
     *
     * @throws IllegalStateException when another plugin already holds {@code key} in that scope, naming both
     */
    @Nonnull
    public static Cancellable override(@Nonnull ECPluginData plugin, @Nonnull ScreenKey key, @Nonnull ScreenScope scope,
                                       @Nonnull Function<ViewContext, Screen<?>> factory) {
        Registered registered = new Registered(plugin, key, scope, factory);
        synchronized (OVERRIDES) {
            Map<ScreenScope, Registered> layers = OVERRIDES.computeIfAbsent(key, any -> new LinkedHashMap<>());
            Registered held = layers.get(scope);
            if (held != null) {
                String holder = held.plugin.getMetaInfo().getName();
                String newcomer = plugin.getMetaInfo().getName();
                throw new IllegalStateException(scope.isServer()
                        ? "[" + holder + "] and [" + newcomer + "] both override " + key + " for the whole server. Only one "
                        + "server-wide override fits; keep one, or scope the other with ScreenScope.ownedBy(plugin)."
                        : "[" + holder + "] and [" + newcomer + "] both override " + key + " for the content of ["
                        + scope.getContentOwner().getMetaInfo().getName() + "]. Only one fits per content owner; keep one.");
            }
            layers.put(scope, registered);
        }
        return () -> {
            synchronized (OVERRIDES) {
                Map<ScreenScope, Registered> layers = OVERRIDES.get(key);
                if (layers != null && layers.get(scope) == registered) {
                    layers.remove(scope);
                }
            }
        };
    }

    /**
     * Which layer would answer {@code key} for {@code request}, in words - what {@code /ecore screen which} prints;
     * {@code null} when none does.
     */
    @Nullable
    public static String which(@Nonnull ScreenKey key, @Nonnull ScreenRequest request) {
        Answer answer = resolve(key, request);
        return answer == null ? null : answer.describe();
    }

    /**
     * The screen {@code key} resolves to for {@code viewer} through the four layers of {@link #open(FPlayer, ScreenKey,
     * ScreenRequest)}, built but not opened - for an engine that has to know which screen it showed. Calling thread.
     *
     * @throws IllegalStateException that teaches, when nothing answers {@code key} or the factory built nothing
     */
    @Nonnull
    public static Screen<?> build(@Nonnull FPlayer viewer, @Nonnull ScreenKey key, @Nonnull ScreenRequest request) {
        Answer answer = resolve(key, request);
        if (answer == null) {
            throw new IllegalStateException("No screen answers [" + key + "] for " + viewer.getName() + ": nothing registered "
                    + "it with Screens.override and the core has no default under that key. Register the screen, or open "
                    + "your Screen object directly with screen.open(viewer).");
        }
        Screen<?> screen = answer.factory.apply(new ViewContext(viewer, answer.key, request));
        if (screen == null) {
            throw new IllegalStateException(answer.describe() + " built no screen for " + viewer.getName()
                    + "; a factory answers a Screen or is not registered.");
        }
        if (screen.getKey() == null) {
            screen.key(answer.key);
        }
        if (screen.getOwner() == null) {
            screen.owner(answer.plugin != null ? answer.plugin : request.getOwner());
        }
        return screen;
    }

    @Nullable
    private static Answer resolve(ScreenKey key, ScreenRequest request) {
        ScreenKey asked = request.getScreen();
        if (asked != null && !asked.equals(key)) {
            Answer replacement = resolveLayers(asked, request);
            if (replacement == null) {
                throw new IllegalStateException("The caller asked for [" + asked + "] in place of [" + key + "], and nothing "
                        + "answers [" + asked + "]. Register it with Screens.override, or drop withScreen from the request.");
            }
            return new Answer(Layer.CALLER, replacement.plugin, asked, replacement.factory);
        }
        return resolveLayers(key, request);
    }

    @Nullable
    private static Answer resolveLayers(ScreenKey key, ScreenRequest request) {
        synchronized (OVERRIDES) {
            Map<ScreenScope, Registered> layers = OVERRIDES.get(key);
            if (layers != null) {
                Registered owned = request.getOwner() == null ? null : layers.get(ScreenScope.ownedBy(request.getOwner()));
                if (owned != null) {
                    return new Answer(Layer.CONTENT_OWNER, owned.plugin, key, owned.factory);
                }
                Registered server = layers.get(ScreenScope.server());
                if (server != null) {
                    return new Answer(Layer.SERVER, server.plugin, key, server.factory);
                }
            }
        }
        Function<ViewContext, Screen<?>> fallback = CoreScreens.defaultOf(key);
        return fallback == null ? null : new Answer(Layer.CORE_DEFAULT, null, key, fallback);
    }

    private enum Layer {
        CALLER("the caller's replacement"),
        CONTENT_OWNER("the content owner's override"),
        SERVER("the server override"),
        CORE_DEFAULT("the core's default");

        private final String text;

        Layer(String text) {
            this.text = text;
        }
    }

    private static final class Registered {

        private final ECPluginData plugin;
        private final ScreenKey key;
        private final ScreenScope scope;
        private final Function<ViewContext, Screen<?>> factory;

        private Registered(ECPluginData plugin, ScreenKey key, ScreenScope scope, Function<ViewContext, Screen<?>> factory) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.key = Objects.requireNonNull(key, "key");
            this.scope = Objects.requireNonNull(scope, "scope");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

    }

    private static final class Answer {

        private final Layer layer;
        private final ECPluginData plugin;
        private final ScreenKey key;
        private final Function<ViewContext, Screen<?>> factory;

        private Answer(Layer layer, @Nullable ECPluginData plugin, ScreenKey key, Function<ViewContext, Screen<?>> factory) {
            this.layer = layer;
            this.plugin = plugin;
            this.key = key;
            this.factory = factory;
        }

        private String describe() {
            return key + ": " + layer.text + (plugin == null ? "" : " by [" + plugin.getMetaInfo().getName() + "]");
        }

    }

    /** Opens a screen inside the chain the viewer is already in, on the caller's (renderer) thread. */
    @Nonnull
    static CompletableFuture<ScreenView> openWithinChain(Screen<?> screen, FPlayer viewer, Object[] args) {
        CompletableFuture<ScreenView> future = new CompletableFuture<>();
        try {
            Pick choice = choose(viewer, screen, args, 0);
            openOnRenderThread(choice.screen, viewer, choice.renderer, args, future, false);
        } catch (IllegalStateException refused) {
            future.completeExceptionally(refused);
        }
        return future;
    }

    /**
     * Builds {@code screen} for {@code viewer} already suspended - armed, but with no window until the chain resumes it.
     * What replacing a screen that is not on top produces. No {@link ScreenOpenEvent}: no window is up. Renderer thread.
     *
     * @throws IllegalStateException when no renderer draws it for this viewer
     */
    @Nonnull
    static ScreenView buildSuspended(Screen<?> screen, FPlayer viewer, Object[] args) {
        Pick choice = choose(viewer, screen, args, 0);
        ScreenView view = new ScreenView(choice.screen, viewer, choice.renderer, args);
        view.suspend();
        view.start();
        return view;
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
    private static Pick choose(FPlayer viewer, Screen<?> screen, Object[] args, int fallbacks) {
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
        ScreenFallback fallback = screen.getOnMissingCapability();
        Screen<?> alternative = fallback.getAlternative();
        if (alternative == null && fallback.getAlternativeKey() != null && fallbacks < MAX_FALLBACKS) {
            alternative = build(viewer, fallback.getAlternativeKey(), ScreenRequest.of(screen.getOwner()).args(args));
        }
        if (alternative != null) {
            if (fallbacks >= MAX_FALLBACKS) {
                throw new IllegalStateException("The screen [" + screen.describe() + "] falls back " + MAX_FALLBACKS
                        + " times in a row and still finds no renderer for " + viewer.getName() + "; the fallbacks loop. "
                        + "End the chain with a screen whose onMissingCapability is REFUSE.");
            }
            return choose(viewer, alternative, args, fallbacks + 1);
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
     * Closes every open screen ({@link CloseReason#SHUTDOWN}) and releases every one set aside. Runs before the
     * platform's listeners go, so each screen still gets its {@code onClose} - a shutdown with an editable screen open
     * would otherwise turn its contents into free loot. Any thread: each screen closes on its renderer's thread, and the
     * call waits for them, at most 5 seconds.
     */
    public static void closeAll() {
        List<ScreenView> setAside = ScreenNavigation.detachAll();
        List<ScreenView> views;
        synchronized (OPEN) {
            views = new ArrayList<>(OPEN.values());
            OPEN.clear();
        }
        closeOnRendererThreads(views, setAside, CloseReason.SHUTDOWN);
    }

    /**
     * Takes back every override {@code owner} registered, then closes every open screen whose content it owns, or whose
     * renderer it registered - the plugin's shutdown. Any thread, as {@link #closeAll()}.
     */
    public static void closeOwnedBy(@Nonnull ECPluginData owner) {
        synchronized (OVERRIDES) {
            for (Map<ScreenScope, Registered> layers : OVERRIDES.values()) {
                layers.values().removeIf(registered -> registered.plugin == owner || registered.scope.getContentOwner() == owner);
            }
        }
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
        List<ScreenView> setAside = new ArrayList<>();
        for (ScreenView view : owned) {
            setAside.addAll(ScreenNavigation.detach(view.getViewerId()));
        }
        closeOnRendererThreads(owned, setAside, CloseReason.SHUTDOWN);
    }

    /** How long {@link #closeAll()} waits for the renderer threads before it gives up on the screens left. */
    private static final long CLOSE_WAIT_SECONDS = 5L;

    /**
     * Closes {@code windowed} with {@code reason} and releases {@code setAside} (which have no window), each on its
     * renderer's thread - inline when the caller is already there - and waits for every one of them.
     */
    private static void closeOnRendererThreads(List<ScreenView> windowed, List<ScreenView> setAside, CloseReason reason) {
        Map<ScreenScheduler, List<Runnable>> batches = new IdentityHashMap<>();
        Set<ScreenView> pending = ConcurrentHashMap.newKeySet();
        for (ScreenView view : windowed) {
            pending.add(view);
            batchOf(batches, view).add(() -> {
                close(view, reason);
                pending.remove(view);
            });
        }
        for (ScreenView view : setAside) {
            pending.add(view);
            batchOf(batches, view).add(() -> {
                release(view, CloseReason.REQUESTED);
                pending.remove(view);
            });
        }
        CountDownLatch done = new CountDownLatch(batches.size());
        for (Map.Entry<ScreenScheduler, List<Runnable>> batch : batches.entrySet()) {
            Runnable task = () -> {
                try {
                    batch.getValue().forEach(Runnable::run);
                } finally {
                    done.countDown();
                }
            };
            try {
                batch.getKey().run(task);
            } catch (RuntimeException threadGone) {
                //a renderer whose thread is already gone takes no task: close them here rather than leave them open
                task.run();
            }
        }
        try {
            if (!done.await(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                List<String> viewers = new ArrayList<>();
                for (ScreenView view : pending) {
                    viewers.add(view.getViewerName());
                }
                EverNifeCore.getLog().severe("The screens of {} did not close within {}s: their renderer thread did not run the "
                        + "close. Their onClose runs only if that thread wakes up, and whatever they hold is not given back "
                        + "before it.", viewers, CLOSE_WAIT_SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<Runnable> batchOf(Map<ScreenScheduler, List<Runnable>> batches, ScreenView view) {
        return batches.computeIfAbsent(view.getScheduler(), scheduler -> new ArrayList<>());
    }

    private static void close(ScreenView view, CloseReason reason) {
        ScreenSurface surface = view.getSurface();
        release(view, reason);
        if (surface != null) {
            try {
                surface.close(reason);
            } catch (Throwable e) {
                EverNifeCore.getLog().severe("The window of [{}] failed to close", view.getViewerName(), e);
            }
        }
    }

    private static void release(ScreenView view, CloseReason reason) {
        try {
            view.release(reason);
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("A screen of [{}] failed to close; the screens after it are still being closed",
                    view.getViewerName(), e);
        }
    }

}
