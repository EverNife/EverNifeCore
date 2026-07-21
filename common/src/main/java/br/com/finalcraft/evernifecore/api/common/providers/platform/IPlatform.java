package br.com.finalcraft.evernifecore.api.common.providers.platform;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface IPlatform {

    /**
     * The stable identity-provider tag of this platform ({@code "minecraft"}, {@code "hytale"}, ...),
     * used by the account layer to tag platform uuid identities. It is persisted inside account rows,
     * so it must be unique per platform and must NEVER change.
     */
    public String getPlatformProviderId();

    public List<FPlayer> getOnlinePlayers();

    public FPlayer getPlayer(String playerName);

    public FPlayer getPlayer(UUID playerUuid);

    public boolean isPluginLoaded(String pluginName);

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
     * Runs the task on the platform's main/server thread and returns a future that completes when it
     * finishes (exceptionally if it throws). When called from another thread - or during enable - the
     * task is deferred to the next opportunity rather than run in place. Timing is platform-specific:
     * <ul>
     *   <li><b>Bukkit:</b> the server main thread. On the next tick when called from enable or another
     *       thread, so a task scheduled during enable fires on the first tick, after every plugin has
     *       enabled. Running there holds the tick until the task finishes - the first-boot legacy
     *       import relies on that to freeze the server while it migrates.</li>
     *   <li><b>Hytale:</b> there is no single main thread (schedulers are per-world), so the task runs
     *       on a background thread. It still waits for the start phase - once every plugin has finished
     *       {@code setup()} and all worlds are loaded: tasks submitted before that are buffered and
     *       released then; tasks submitted afterwards run right away.</li>
     * </ul>
     */
    public CompletableFuture<Void> runOnMainThread(Runnable task);

    /**
     * The value-returning form of {@link #runOnMainThread(Runnable)}: runs the supplier under the same
     * per-platform contract and completes the future with its result (exceptionally if it throws).
     */
    public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task);

    /**
     * Registers the platform's config types (Bukkit {@code ItemStack}/{@code Location}, Hytale vectors, ...)
     * on {@link br.com.finalcraft.evernifecore.config.ConfigFactory}, teaching the Jackson engine how they cross to
     * and from config. Called once at bootstrap, before any config is opened. Implementations MUST be
     * idempotent - a repeated call must be a no-op.
     */
    public void registerConfigTypes();

    public void registerArgParsers();

}
