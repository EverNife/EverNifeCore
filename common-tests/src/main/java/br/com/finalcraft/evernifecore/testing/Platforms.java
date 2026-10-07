package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Builds the platform a test runs against, and installs it.
 *
 * <pre>{@code
 * try (ECoreTestWorld world = Platforms.lenient().install()) {
 *     ...
 * }
 * }</pre>
 *
 * <p>{@link #strict()} answers nothing until told to; {@link #lenient()} starts from the defaults
 * this repository used before the test engine existed. Strict is the honest default - a test that
 * reaches a corner of the platform nobody modelled should say so, not read a {@code null} that
 * looks like a decision.</p>
 */
public final class Platforms {

    private final TestPlatform platform = new TestPlatform();

    private Platforms() {
    }

    /** A platform that refuses every question it was not taught to answer. */
    public static Platforms strict() {
        return new Platforms();
    }

    /**
     * A platform with the old no-op answers: no players, no plugins, no PAPI, no action bar, no
     * chat/vec adapter, listeners and config types ignored, main thread inline (every world thread too),
     * world load/unload events kept for the test to fire, shutdown recorded, every world 0..255 high, no outline
     * renderer and no selection item ({@code NONE} for both).
     */
    public static Platforms lenient() {
        return strict()
                .platformProviderId("test")
                .onlinePlayers(Collections.<FPlayer>emptyList())
                .pluginsLoaded()
                .papiPresent(false)
                .actionBarSupported(false)
                .parsingWith(new BiFunction<FPlayer, String, String>() {
                    @Override
                    public String apply(FPlayer player, String text) {
                        return text;
                    }
                })
                .chatAdapter(null)
                .vecAdapter(null)
                .ignoringListeners()
                .ignoringPlatformRegistrations()
                .loggingToStdout()
                .mainThreadInline()
                .worldEvents()
                .recordingShutdowns()
                .withoutPlaceholderIntegration()
                .worldHeight(0, 255)
                .outlineRenderer(IOutlineRenderer.NONE)
                .selectionItems(ISelectionItems.NONE);
    }

    /**
     * The lenient defaults plus command capture and a chat adapter that has no opinion - the shape
     * a command test needs.
     */
    public static Platforms commandCapture() {
        return lenient()
                .neutralChatAdapter()
                .capturingCommands();
    }

    public Platforms platformProviderId(String platformProviderId) {
        platform.platformProviderId = platformProviderId;
        return this;
    }

    public Platforms onlinePlayers(List<FPlayer> onlinePlayers) {
        platform.onlinePlayers = new ArrayList<FPlayer>(onlinePlayers);
        return this;
    }

    /** Plugins installed and running - both {@code isPluginLoaded} and {@code isPluginEnabled} answer true. */
    public Platforms pluginsLoaded(String... pluginNames) {
        platform.loadedPlugins = Arrays.asList(pluginNames);
        return this;
    }

    /**
     * Plugins installed but not running - a plugin that failed to enable, or was disabled: only
     * {@code isPluginLoaded} answers true. Needs {@link #pluginsLoaded(String...)} too, even an empty one.
     */
    public Platforms pluginsDisabled(String... pluginNames) {
        platform.disabledPlugins = Arrays.asList(pluginNames);
        return this;
    }

    public Platforms papiPresent(boolean papiPresent) {
        platform.papiPresent = papiPresent;
        return this;
    }

    /**
     * Whether this server can show an action bar at all - 1.7.10 without NecroTempus cannot, and the
     * code that sends one asks before building anything.
     *
     * <p>It does not decide what is recorded: either way, an action bar that does reach this platform
     * is kept for {@link TestPlatform#getActionBars()}, the same as a real one would have written the
     * packet without asking again.</p>
     */
    public Platforms actionBarSupported(boolean actionBarSupported) {
        platform.actionBarSupported = actionBarSupported;
        return this;
    }

    /** {@code null} is a valid answer: it is what "this platform has no chat adapter" looks like. */
    public Platforms chatAdapter(IPlatformChatAdapter chatAdapter) {
        platform.chatAdapterConfigured = Boolean.TRUE;
        platform.chatAdapter = chatAdapter;
        return this;
    }

    /** A chat adapter that answers every question with "no opinion": centered text is the text itself, empty audience, hover supported. */
    public Platforms neutralChatAdapter() {
        return chatAdapter(TestPlatform.neutralChatAdapter());
    }

    public Platforms vecAdapter(IPlatformVecAdapter vecAdapter) {
        platform.vecAdapterConfigured = Boolean.TRUE;
        platform.vecAdapter = vecAdapter;
        return this;
    }

    /** The height every world answers, inclusive on both ends. */
    public Platforms worldHeight(int minY, int maxY) {
        platform.worldHeight = WorldHeight.of(minY, maxY);
        return this;
    }

    /** What {@code getOutlineRenderer} answers; {@code null} is kept, as the violation a conformance test needs. */
    public Platforms outlineRenderer(IOutlineRenderer outlineRenderer) {
        platform.outlineRendererConfigured = Boolean.TRUE;
        platform.outlineRenderer = outlineRenderer;
        return this;
    }

    /** A renderer that keeps every frame for {@link TestPlatform#getRenderedOutlines()} instead of drawing it. */
    public Platforms recordingOutlines() {
        return outlineRenderer(platform.recordingRenderer());
    }

    /** What {@code getSelectionItems} answers; {@code null} is kept, as the violation a conformance test needs. */
    public Platforms selectionItems(ISelectionItems selectionItems) {
        platform.selectionItemsConfigured = Boolean.TRUE;
        platform.selectionItems = selectionItems;
        return this;
    }

    /** Keeps every tool handed out for {@link TestPlatform#getSelectionItemsGiven()} instead of giving it. */
    public Platforms recordingSelectionItems() {
        return selectionItems(platform.recordingItems());
    }

    public Platforms parsingWith(BiFunction<FPlayer, String, String> parser) {
        platform.parser = parser;
        return this;
    }

    public Platforms ignoringListeners() {
        platform.listenersIgnored = Boolean.TRUE;
        return this;
    }

    /** Makes {@code registerConfigTypes}/{@code registerArgParsers} no-ops instead of refusals. */
    public Platforms ignoringPlatformRegistrations() {
        platform.configTypesIgnored = Boolean.TRUE;
        return this;
    }

    public Platforms loggingToStdout() {
        platform.stdoutLogging = Boolean.TRUE;
        return this;
    }

    public Platforms withoutPlaceholderIntegration() {
        platform.placeholderIntegrationNull = Boolean.TRUE;
        return this;
    }

    /**
     * Runs both main-thread forms (inline and next-tick) and {@code runOnWorldThread} in place, and answers every
     * thread as the owner of every world - which is what makes a boot sequence deterministic in a test.
     */
    public Platforms mainThreadInline() {
        platform.mainThreadInline = Boolean.TRUE;
        return this;
    }

    /**
     * Answers {@code isWorldThread} with false for every world while {@code runOnWorldThread} still runs inline - the
     * caller that should have been on a world's thread and is not.
     */
    public Platforms offWorldThread() {
        platform.worldThreadAnswer = Boolean.FALSE;
        return this;
    }

    /**
     * Keeps the {@code onWorldLoaded}/{@code onWorldUnloaded} listeners so the test can fire them with
     * {@link TestPlatform#loadWorld(String)} and {@link TestPlatform#unloadWorld(String)}.
     */
    public Platforms worldEvents() {
        platform.worldEvents = Boolean.TRUE;
        return this;
    }

    /** Records shutdown reasons instead of stopping the server - a real shutdown would kill the test JVM. */
    public Platforms recordingShutdowns() {
        platform.recordingShutdowns = Boolean.TRUE;
        return this;
    }

    /**
     * Captures command registration/unregistration so a test can assert on the dispatch flow, and the
     * commands the code under test asks the server to RUN - see {@link TestPlatform#getConsoleCommands()}
     * and {@link TestPlatform#getSenderCommands()}. Both answer {@code true}, which is what a server
     * that took the command answers.
     */
    public Platforms capturingCommands() {
        platform.capturingCommands = Boolean.TRUE;
        return this;
    }

    /** The configured double, not installed anywhere. Use {@link #install()} for the usual case. */
    public TestPlatform build() {
        return platform;
    }

    /** Registers this platform as the current one and hands back the way to put the old world back. */
    public ECoreTestWorld install() {
        return ECoreTestWorld.install(platform);
    }
}
