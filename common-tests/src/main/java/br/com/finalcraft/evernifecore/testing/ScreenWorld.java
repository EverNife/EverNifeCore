package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickKind;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenRenderer;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

/**
 * Screens without a server: a strict platform with only what a screen asks for, a {@link RecordingRenderer} registered
 * by a fake plugin named {@code test}, and a clock in ticks the test moves. Viewers are {@link TestFPlayerSender}s.
 *
 * <p>{@link #close()} fails when the test left a view open or a source held needed - what a screen forgot to release
 * would otherwise leak into the next test - after cleaning both up.</p>
 */
public final class ScreenWorld implements AutoCloseable {

    /** The fake plugin every recording renderer is registered by. */
    public static final String OWNER = "test";

    private final ECoreTestWorld world;
    private final ECPluginData owner;
    private final TickScheduler ticks;
    private final RecordingRenderer renderer;
    private final List<FPlayer> viewers = new ArrayList<>();
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();
    private final Map<Class<?>, List<?>> recorded = new HashMap<>();
    private LongConsumer ticker;
    private Consumer<String> answerer;
    private boolean closed;

    private ScreenWorld(Path dataFolder, UnaryOperator<Platforms> platform, RendererCapability[] capabilities) {
        this.world = platform.apply(Platforms.strict()
                .ignoringPlatformRegistrations()
                .mainThreadInline()
                .neutralChatAdapter()
                .papiPresent(false)
                .loggingToStdout()
                .onlinePlayers(viewers))
                .install();
        this.owner = Plugins.fakePluginData(OWNER, dataFolder.toFile());
        this.ticks = new TickScheduler();
        this.ticker = ticks::advanceTicks;
        this.renderer = new RecordingRenderer(ticks, capabilities);
        this.answerer = renderer::answer;
        Screens.registerRenderer(owner, renderer);
    }

    /** A world whose recording renderer has {@code capabilities}; none by default. */
    @Nonnull
    public static ScreenWorld open(@Nonnull Path dataFolder, @Nonnull RendererCapability... capabilities) {
        return new ScreenWorld(dataFolder, UnaryOperator.identity(), capabilities);
    }

    /**
     * A world whose platform is the usual one adjusted by {@code platform} - {@code Platforms::offWorldThread} for a
     * renderer that has to prove it hops to the viewer's world thread before touching the server.
     */
    @Nonnull
    public static ScreenWorld open(@Nonnull Path dataFolder, @Nonnull UnaryOperator<Platforms> platform,
                                   @Nonnull RendererCapability... capabilities) {
        return new ScreenWorld(dataFolder, platform, capabilities);
    }

    /** The plugin named {@code test}: it owns the recording renderer and may register more {@code test:*} ones. */
    @Nonnull
    public ECPluginData getOwner() {
        return owner;
    }

    /** The strict platform under the screens - its logged lines are what a layout warning is asserted on. */
    @Nonnull
    public TestPlatform getPlatform() {
        return world.platform();
    }

    @Nonnull
    public RecordingRenderer getRenderer() {
        return renderer;
    }

    @Nonnull
    public TickScheduler getTicks() {
        return ticks;
    }

    /** What {@link #advanceTicks(long)} moves - another renderer's clock, for a renderer under conformance. */
    @Nonnull
    public ScreenWorld tickingWith(@Nonnull LongConsumer ticker) {
        this.ticker = ticker;
        return this;
    }

    /** How {@link #answerPrompt(String)} answers - another renderer's channel, for a renderer under conformance. */
    @Nonnull
    public ScreenWorld answeringPromptsWith(@Nonnull Consumer<String> answerer) {
        this.answerer = answerer;
        return this;
    }

    /** Answers the prompt waiting on the viewer as if they had typed {@code text}. */
    public void answerPrompt(@Nonnull String text) {
        answerer.accept(text);
    }

    /** Registers another renderer under the {@code test} plugin; it is taken out when the world closes. */
    @Nonnull
    public ScreenWorld withRenderer(@Nonnull ScreenRenderer other) {
        Screens.registerRenderer(owner, other);
        return this;
    }

    /** An online viewer the platform knows. */
    @Nonnull
    public TestFPlayerSender viewer(@Nonnull String name) {
        TestFPlayerSender viewer = new TestFPlayerSender(name);
        viewers.add(viewer);
        world.platform().setOnlinePlayers(viewers);
        return viewer;
    }

    /** Opens {@code screen} and waits for the window. */
    @Nonnull
    public ScreenView open(@Nonnull Screen<?> screen, @Nonnull FPlayer viewer, @Nonnull Object... args) {
        return tryOpen(screen, viewer, args).join();
    }

    /** Opens {@code screen}, handing back the future as it stands - refused ones included. */
    @Nonnull
    public CompletableFuture<ScreenView> tryOpen(@Nonnull Screen<?> screen, @Nonnull FPlayer viewer, @Nonnull Object... args) {
        return Screens.open(viewer, screen, args);
    }

    /** Moves the clock: every pass, watch and navigation due runs, in order. */
    public void advanceTicks(long ticks) {
        ticker.accept(ticks);
    }

    /** A click on the element {@code elementId}, the way a renderer that draws by element sends one. */
    public boolean click(@Nonnull ScreenView view, @Nonnull String elementId, @Nonnull ClickGesture gesture) {
        return view.click(elementId, gesture);
    }

    /** A click on {@code slot}, the way a grid renderer sends one. */
    public boolean click(@Nonnull ScreenView view, int slot, @Nonnull ClickGesture gesture, @Nonnull ClickKind kind) {
        return view.click(slot, gesture, kind, null);
    }

    /** Every {@code type} posted on the global bus from now on. */
    @SuppressWarnings("unchecked")
    @Nonnull
    public synchronized <T extends IECEvent> List<T> recorded(@Nonnull Class<T> type) {
        List<T> seen = (List<T>) recorded.get(type);
        if (seen == null) {
            List<T> created = new CopyOnWriteArrayList<>();
            subscriptions.add(ECEventBus.global().subscribe(type, created::add));
            recorded.put(type, created);
            seen = created;
        }
        return seen;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<String> leaked = new ArrayList<>();
        if (Screens.getOpenCount() > 0) {
            leaked.add(Screens.getOpenCount() + " open view(s)");
        }
        Screens.closeAll();
        for (EventSource source : ActionEvents.all()) {
            if (EventSourceUsage.get().isNeeded(source)) {
                leaked.add("source " + source + " still needed");
            }
        }
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        Screens.unregisterRenderers(owner);
        Plugins.forget(OWNER);
        try {
            world.close();
        } finally {
            if (!leaked.isEmpty()) {
                throw new AssertionError("The test left " + leaked + " behind. Close every view it opened (view.close(), "
                        + "then advance a tick) before the world closes - a view holds its sources needed and its "
                        + "tasks alive until it is released.");
            }
        }
    }

    /** The core's own plugin data, the owner screens without one run their logic as. */
    @Nonnull
    public ECPluginData getCore() {
        return EverNifeCore.getEcPluginData();
    }

}
