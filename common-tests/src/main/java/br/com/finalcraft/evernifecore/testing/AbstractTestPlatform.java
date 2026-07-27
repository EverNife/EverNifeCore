package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Strict base for every platform double: each method refuses instead of answering, naming itself
 * and the call that would configure it.
 *
 * <p>Two properties come from being a concrete class rather than an anonymous {@code implements
 * IPlatform}. A method added to the interface reaches every double with a working (refusing)
 * body, so a double only breaks when it actually overrode the changed method. And a test that
 * wanders into a corner of the platform nobody modelled fails loudly instead of quietly reading a
 * {@code null} or a {@code false} that no one chose.</p>
 *
 * <p>Subclass this only to build a new kind of double; to configure one, use {@link Platforms}.</p>
 */
public abstract class AbstractTestPlatform implements IPlatform {

    /**
     * @param method the refusing method, as it reads in {@link IPlatform}
     * @param hint   the builder call that teaches this double how to answer
     */
    protected UnsupportedOperationException notConfigured(String method, String hint) {
        return new UnsupportedOperationException(
                "IPlatform#" + method + " not configured on this test platform - use " + hint
                        + " (or Platforms.lenient() for the old no-op defaults)");
    }

    @Override
    public String getPlatformProviderId() {
        throw notConfigured("getPlatformProviderId", "Platforms.strict().platformProviderId(\"test\")");
    }

    @Override
    public List<FPlayer> getOnlinePlayers() {
        throw notConfigured("getOnlinePlayers", "Platforms.strict().onlinePlayers(...)");
    }

    @Override
    public FPlayer getPlayer(String playerName) {
        throw notConfigured("getPlayer(String)", "Platforms.strict().onlinePlayers(...)");
    }

    @Override
    public FPlayer getPlayer(UUID playerUuid) {
        throw notConfigured("getPlayer(UUID)", "Platforms.strict().onlinePlayers(...)");
    }

    @Override
    public boolean isPluginLoaded(String pluginName) {
        throw notConfigured("isPluginLoaded", "Platforms.strict().pluginsLoaded(\"" + pluginName + "\")");
    }

    @Override
    public boolean makeConsoleExecuteCommand(String command) {
        throw notConfigured("makeConsoleExecuteCommand", "Platforms.strict().capturingCommands()");
    }

    @Override
    public boolean makePlayerExecuteCommand(FCommandSender sender, String command) {
        throw notConfigured("makePlayerExecuteCommand", "Platforms.strict().capturingCommands()");
    }

    @Override
    public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand) {
        throw notConfigured("registerCommand", "Platforms.strict().capturingCommands()");
    }

    @Override
    public void unregisterCommand(String commandName, ECPluginData notifyPlugin) {
        throw notConfigured("unregisterCommand", "Platforms.strict().capturingCommands()");
    }

    @Override
    public void registerECListener(ECPluginData ecPluginData, ECListener listener) {
        throw notConfigured("registerECListener", "Platforms.lenient()");
    }

    @Override
    public void unregisterECListener(ECListener listener) {
        throw notConfigured("unregisterECListener", "Platforms.lenient()");
    }

    @Override
    public boolean isPAPIPresent() {
        throw notConfigured("isPAPIPresent", "Platforms.strict().papiPresent(false)");
    }

    @Override
    public String parse(FPlayer player, String text) {
        throw notConfigured("parse", "Platforms.strict().parsingWith(...)");
    }

    @Override
    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(ECPluginData plugin, String pluginBaseID, Class<P> playerDataType) {
        throw notConfigured("createPlaceholderIntegration", "Platforms.lenient()");
    }

    @Override
    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {
        throw notConfigured("createLogAdapterFor", "Platforms.strict().loggingToStdout()");
    }

    @Override
    public void sendActionBarMessage(FPlayer player, FancyText fancyText) {
        throw notConfigured("sendActionBarMessage", "Platforms.strict().actionBarSupported(true)");
    }

    @Override
    public boolean serverSupportsActionBar() {
        throw notConfigured("serverSupportsActionBar", "Platforms.strict().actionBarSupported(false)");
    }

    @Override
    public IPlatformVecAdapter getVecAdapter() {
        throw notConfigured("getVecAdapter", "Platforms.strict().vecAdapter(...)");
    }

    @Override
    public IPlatformChatAdapter getChatAdapter() {
        throw notConfigured("getChatAdapter", "Platforms.strict().chatAdapter(...)");
    }

    @Override
    public void registerConfigTypes() {
        throw notConfigured("registerConfigTypes", "Platforms.lenient()");
    }

    @Override
    public void registerArgParsers() {
        throw notConfigured("registerArgParsers", "Platforms.lenient()");
    }

    @Override
    public CompletableFuture<Void> runOnMainThread(Runnable task) {
        throw notConfigured("runOnMainThread(Runnable)", "Platforms.strict().mainThreadInline()");
    }

    @Override
    public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) {
        throw notConfigured("runOnMainThread(Supplier)", "Platforms.strict().mainThreadInline()");
    }

    @Override
    public void shutdown(String reason) {
        throw notConfigured("shutdown", "Platforms.strict().recordingShutdowns()");
    }
}
