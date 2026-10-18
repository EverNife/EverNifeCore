package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.region.selection.ISelectionItems;
import br.com.finalcraft.evernifecore.region.shape.WorldHeight;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.terrain.ITerrainAccess;
import br.com.finalcraft.evernifecore.region.visual.IOutlineRenderer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IPlatform {

    /**
     * The stable identity-provider tag of this platform - {@link PlatformId} names the ones already
     * taken. The account layer tags platform uuid identities with it and persists it inside account
     * rows, so it must be unique per platform and must NEVER change.
     */
    public String getPlatformProviderId();

    public List<FPlayer> getOnlinePlayers();

    public FPlayer getPlayer(String playerName);

    public FPlayer getPlayer(UUID playerUuid);

    /**
     * Whether a plugin named {@code pluginName} is installed on this server - its jar was picked up,
     * whether or not it is running. Enough to know its classes can be reached; use
     * {@link #isPluginEnabled(String)} before calling into it.
     */
    public boolean isPluginLoaded(String pluginName);

    /**
     * Whether a plugin named {@code pluginName} is installed AND running right now - the question to
     * ask before calling its API. An enabled plugin is always a loaded one.
     */
    public boolean isPluginEnabled(String pluginName);

    public boolean makeConsoleExecuteCommand(String command);

    public boolean makePlayerExecuteCommand(FCommandSender sender, String command);

    public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand);

    public void unregisterCommand(String commandName, ECPluginData notifyPlugin);

    public void registerECListener(ECPluginData ecPluginData, ECListener listener);

    public void unregisterECListener(ECListener listener);

    public boolean isPAPIPresent();

    public String parse(@Nullable FPlayer player, @Nonnull String text);

    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType);

    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData);

    public void sendActionBarMessage(FPlayer player, FancyText fancyText);

    public boolean serverSupportsActionBar();

    public IPlatformVecAdapter getVecAdapter();

    public IPlatformChatAdapter getChatAdapter();

    /**
     * The block Y range of the world named {@code worldName}, inclusive on both ends. A world that is not
     * loaded (or does not exist) answers the platform's default height; this never throws.
     */
    public WorldHeight getWorldHeight(String worldName);

    /**
     * The spawn point of the world named {@code worldName} - the world's own, not a plugin's server spawn - or
     * {@code null} when that world is not loaded or does not exist. Never throws.
     */
    public @Nullable WorldLocPos getWorldSpawn(String worldName);

    /**
     * Whether the calling thread owns the world named {@code worldName} and may touch its blocks, entities
     * and players. Bukkit: the server main thread, whatever the world. Hytale: that world's own ticking thread;
     * a world that is not loaded answers {@code false}. Never throws.
     */
    public boolean isWorldThread(String worldName);

    /**
     * Runs the task on the thread that owns the world named {@code worldName}: inline when the caller already
     * is that thread, queued otherwise (Bukkit: the main thread; Hytale: the world's own executor). The future
     * completes exceptionally when the platform has no thread for that world - on Hytale, a world that is not
     * loaded: load the world first, or drop work meant for a world that is gone.
     */
    public CompletableFuture<Void> runOnWorldThread(String worldName, Runnable task);

    /**
     * Runs the task on the thread that owns the world named {@code worldName}, on that world's next tick - never in
     * the current one, even when the caller is that thread, so work split across calls is paced one piece a tick
     * (Bukkit: one tick later on the main thread; Hytale: one tick step later on the world's executor). The future
     * completes exceptionally as {@link #runOnWorldThread} does when the platform has no thread for that world.
     */
    public CompletableFuture<Void> runOnWorldThreadNextTick(String worldName, Runnable task);

    /**
     * Keeps {@code listener} for the life of the process and calls it with the world's name each time a world
     * loads. The core registers once; there is no way to remove a listener.
     */
    public void onWorldLoaded(Consumer<String> listener);

    /** As {@link #onWorldLoaded(Consumer)}, each time a world unloads. */
    public void onWorldUnloaded(Consumer<String> listener);

    /**
     * How this platform draws region outlines and live selections. {@link IOutlineRenderer#NONE} is the honest
     * answer of a platform that does not draw; {@code null} is a violation.
     */
    public IOutlineRenderer getOutlineRenderer();

    /**
     * How this platform hands out the region selection tool. {@link ISelectionItems#NONE} is the honest answer of
     * a platform that gives no item; {@code null} is a violation.
     */
    public ISelectionItems getSelectionItems();

    /**
     * How this platform reads and writes blocks and entities by position, for region terrain.
     * {@link ITerrainAccess#UNSUPPORTED} is the honest answer of a platform that cannot; {@code null} is a violation.
     */
    public ITerrainAccess getTerrainAccess();

    /**
     * What this platform can read and change on a player, for the session flags. {@link IPlayerStateAccess#UNSUPPORTED}
     * is the honest answer of a platform that can change nothing; {@code null} is a violation.
     */
    public IPlayerStateAccess getPlayerState();

    /**
     * Runs the task on the platform's main/server thread: inline, right now, when the calling thread
     * already is it; deferred exactly like {@link #runOnMainThreadNextTick(Runnable)} when it is not.
     * On a platform with no single main thread (Hytale) the two forms are the same call.
     *
     * <p>For work that must wait for the next tick even when already on the main thread - what
     * enable-time bootstrap relies on - use {@link #runOnMainThreadNextTick(Runnable)}.</p>
     */
    public CompletableFuture<Void> runOnMainThread(Runnable task);

    /**
     * The value-returning form of {@link #runOnMainThread(Runnable)}: same thread contract, and the
     * future completes with the supplier's result (exceptionally if it throws).
     */
    public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task);

    /**
     * Runs the task on the platform's main/server thread, always deferred - never in place, even when
     * already called from it. Timing is platform-specific:
     * <ul>
     *   <li><b>Bukkit:</b> the server main thread, as a zero-delay scheduler task: off the main thread or during
     *       enable that is a later tick, but one scheduled by a task the scheduler is running may run later in that
     *       same tick - pace work a piece per tick with {@link #runOnWorldThreadNextTick}. A task scheduled during enable
     *       fires on the first tick, after every plugin has enabled; one scheduled by a plugin that
     *       enables before EverNifeCore runs after the first-tick tasks of the core's own enable.
     *       Running there holds the tick until the task finishes - the first-boot legacy import
     *       relies on that to freeze the server while it migrates.</li>
     *   <li><b>Hytale:</b> there is no single main thread (schedulers are per-world), so the task runs
     *       on a background thread. It still waits for the start phase - once every plugin has finished
     *       {@code setup()} and all worlds are loaded: tasks submitted before that are buffered and
     *       released then; tasks submitted afterwards run right away.</li>
     * </ul>
     */
    public CompletableFuture<Void> runOnMainThreadNextTick(Runnable task);

    /**
     * The value-returning form of {@link #runOnMainThreadNextTick(Runnable)}: runs the supplier under
     * the same per-platform contract and completes the future with its result (exceptionally if it
     * throws).
     */
    public <T> CompletableFuture<T> runOnMainThreadNextTick(Supplier<T> task);

    /**
     * Registers the platform's config types (Bukkit {@code ItemStack}/{@code Location}, Hytale vectors, ...)
     * on {@link br.com.finalcraft.evernifecore.config.ConfigFactory}, teaching the Jackson engine how they cross to
     * and from config. Called once at bootstrap, before any config is opened. Implementations MUST be
     * idempotent - a repeated call must be a no-op.
     */
    public void registerConfigTypes();

    public void registerArgParsers();

    /**
     * Stops the WHOLE server (not just this plugin), the way the platform's own stop command does -
     * worlds and data get their normal save path. Used when continuing to run would corrupt state,
     * e.g. a storage backend that holds player data is unreachable at boot.
     *
     * <p>Implementations must be safe to call from the platform's boot/main thread and must NOT
     * block waiting for the shutdown to finish: the caller is expected to return (and usually
     * rethrow) so the platform can unwind normally.</p>
     *
     * @param reason short, admin-facing text logged next to the shutdown
     */
    public void shutdown(String reason);

}
