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
     * Runs the task once, as close as each platform allows to "after every plugin has finished
     * enabling". The guarantee is platform-specific:
     * <ul>
     *   <li><b>Bukkit:</b> on the server's main thread on the FIRST tick - a task scheduled during
     *       enable only fires once every plugin is enabled; if the server is already past startup it
     *       runs on the next tick.</li>
     *   <li><b>Hytale:</b> there is no global first-tick hook yet (schedulers are per-world), so the
     *       task currently runs in place, still inside enable - it does NOT wait for other plugins or
     *       for worlds to load.</li>
     * </ul>
     */
    public void runOnFirstTick(Runnable runnable);

    /**
     * Runs the task on the platform's main/server thread (on the next tick when called from another
     * thread). The bridge async storage callbacks use to touch game state safely.
     */
    public void runOnMainThread(Runnable runnable);

    /**
     * Registers the platform's config types (Bukkit {@code ItemStack}/{@code Location}, Hytale vectors, ...)
     * on {@link br.com.finalcraft.evernifecore.config.ConfigFactory}, teaching the Jackson engine how they cross to
     * and from config. Called once at bootstrap, before any config is opened. Implementations MUST be
     * idempotent - a repeated call must be a no-op.
     */
    public void registerConfigTypes();

    public void registerArgParsers();

}
