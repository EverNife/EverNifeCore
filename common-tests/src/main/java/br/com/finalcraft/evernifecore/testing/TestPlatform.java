package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The platform double {@link Platforms} builds. Everything it answers was configured; everything
 * else still refuses, inherited from {@link AbstractTestPlatform}.
 *
 * <p>All capture state is per instance, never static: two test classes sharing a JVM cannot see
 * each other's commands, messages or shutdown reasons.</p>
 */
public class TestPlatform extends AbstractTestPlatform {

    private final Map<String, FinalCMDPluginCommand> capturedByLabel = new LinkedHashMap<>();
    private final List<FinalCMDPluginCommand> registrationOrder = new ArrayList<>();
    private final List<String> unregisteredLabels = new ArrayList<>();
    private final List<String> infoMessages = new ArrayList<>();
    //appended from whatever thread logs (flusher, idle sweep, cache-sync), so never a plain ArrayList
    private final List<String> loggedMessages = new CopyOnWriteArrayList<>();
    private final List<String> shutdownReasons = new ArrayList<>();

    // null means "not configured": the inherited strict method refuses.
    String platformProviderId;
    List<FPlayer> onlinePlayers;
    List<String> loadedPlugins;
    Boolean papiPresent;
    Boolean actionBarSupported;
    Boolean listenersIgnored;
    Boolean configTypesIgnored;
    Boolean mainThreadInline;
    Boolean capturingCommands;
    Boolean recordingShutdowns;
    Boolean stdoutLogging;
    Boolean placeholderIntegrationNull;
    Boolean chatAdapterConfigured;
    IPlatformChatAdapter chatAdapter;
    Boolean vecAdapterConfigured;
    IPlatformVecAdapter vecAdapter;
    BiFunction<FPlayer, String, String> parser;

    private boolean forceRegisterFailure = false;

    TestPlatform() {
    }

    // ------------------------------------------------------------------
    // What a test asserts on
    // ------------------------------------------------------------------

    /** The command captured under {@code label} (primary or extra), or {@code null}. */
    public FinalCMDPluginCommand getCaptured(String label) {
        return capturedByLabel.get(label);
    }

    /** Every captured command, in the order {@code registerCommand} was called. */
    public List<FinalCMDPluginCommand> registrationOrder() {
        return registrationOrder;
    }

    public List<String> getUnregisteredLabels() {
        return unregisteredLabels;
    }

    /** Every {@code info}-level line logged through an adapter this platform created. */
    public List<String> getInfoMessages() {
        return infoMessages;
    }

    /**
     * Every line logged through an adapter this platform created, at ANY level, in order. Some
     * behaviour has no other observable effect - a bind that warns about a risky backend still binds,
     * so the warning is all there is to assert on. {@link Logs} reads this.
     */
    public List<String> getLoggedMessages() {
        return loggedMessages;
    }


    /** Every reason passed to {@code shutdown} - a real shutdown would kill the test JVM. */
    public List<String> getShutdownReasons() {
        return shutdownReasons;
    }

    /** When true, every later {@code registerCommand} rejects without capturing - a platform-level registration failure. */
    public void setForceRegisterFailure(boolean forceRegisterFailure) {
        this.forceRegisterFailure = forceRegisterFailure;
    }

    public void reset() {
        capturedByLabel.clear();
        registrationOrder.clear();
        unregisteredLabels.clear();
        infoMessages.clear();
        loggedMessages.clear();
        shutdownReasons.clear();
    }

    // ------------------------------------------------------------------
    // IPlatform - only what was configured answers
    // ------------------------------------------------------------------

    @Override
    public String getPlatformProviderId() {
        return platformProviderId != null ? platformProviderId : super.getPlatformProviderId();
    }

    @Override
    public List<FPlayer> getOnlinePlayers() {
        return onlinePlayers != null ? onlinePlayers : super.getOnlinePlayers();
    }

    @Override
    public FPlayer getPlayer(String playerName) {
        if (onlinePlayers == null) {
            return super.getPlayer(playerName);
        }
        for (FPlayer player : onlinePlayers) {
            if (player.getName().equals(playerName)) {
                return player;
            }
        }
        return null;
    }

    @Override
    public FPlayer getPlayer(UUID playerUuid) {
        if (onlinePlayers == null) {
            return super.getPlayer(playerUuid);
        }
        for (FPlayer player : onlinePlayers) {
            if (player.getUniqueId().equals(playerUuid)) {
                return player;
            }
        }
        return null;
    }

    @Override
    public boolean isPluginLoaded(String pluginName) {
        return loadedPlugins != null ? loadedPlugins.contains(pluginName) : super.isPluginLoaded(pluginName);
    }

    @Override
    public boolean makeConsoleExecuteCommand(String command) {
        return capturingCommands != null ? false : super.makeConsoleExecuteCommand(command);
    }

    @Override
    public boolean makePlayerExecuteCommand(FCommandSender sender, String command) {
        return capturingCommands != null ? false : super.makePlayerExecuteCommand(sender, command);
    }

    @Override
    public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand) {
        if (capturingCommands == null) {
            return super.registerCommand(finalCMDPluginCommand);
        }
        if (forceRegisterFailure) {
            return false;
        }
        capturedByLabel.put(finalCMDPluginCommand.getPrimaryLabel(), finalCMDPluginCommand);
        for (String extraLabel : finalCMDPluginCommand.getExtraLabels()) {
            capturedByLabel.put(extraLabel, finalCMDPluginCommand);
        }
        registrationOrder.add(finalCMDPluginCommand);
        return true;
    }

    @Override
    public void unregisterCommand(String commandName, ECPluginData notifyPlugin) {
        if (capturingCommands == null) {
            super.unregisterCommand(commandName, notifyPlugin);
            return;
        }
        unregisteredLabels.add(commandName);
        capturedByLabel.remove(commandName);
    }

    @Override
    public void registerECListener(ECPluginData ecPluginData, ECListener listener) {
        if (listenersIgnored == null) {
            super.registerECListener(ecPluginData, listener);
        }
    }

    @Override
    public void unregisterECListener(ECListener listener) {
        if (listenersIgnored == null) {
            super.unregisterECListener(listener);
        }
    }

    @Override
    public boolean isPAPIPresent() {
        return papiPresent != null ? papiPresent : super.isPAPIPresent();
    }

    @Override
    public String parse(FPlayer player, String text) {
        return parser != null ? parser.apply(player, text) : super.parse(player, text);
    }

    @Override
    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(ECPluginData plugin, String pluginBaseID, Class<P> playerDataType) {
        return placeholderIntegrationNull != null ? null : super.createPlaceholderIntegration(plugin, pluginBaseID, playerDataType);
    }

    @Override
    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {
        if (stdoutLogging == null) {
            return super.createLogAdapterFor(ecPluginData);
        }
        //a stdout-backed adapter so ECLogger works headless (a null adapter NPEs on the first log line)
        return new ILogAdapter() {
            @Override
            public void info(String string) {
                infoMessages.add(string);
                loggedMessages.add(string);
                System.out.println(string);
            }

            @Override
            public void warning(String string) {
                loggedMessages.add(string);
                System.out.println("[WARN] " + string);
            }

            @Override
            public void severe(String string) {
                loggedMessages.add(string);
                System.out.println("[SEVERE] " + string);
            }

            @Override
            public void log(java.util.logging.Level level, String string) {
                loggedMessages.add(string);
                System.out.println("[" + level + "] " + string);
            }
        };
    }

    @Override
    public void sendActionBarMessage(FPlayer player, FancyText fancyText) {
        if (actionBarSupported == null) {
            super.sendActionBarMessage(player, fancyText);
        }
    }

    @Override
    public boolean serverSupportsActionBar() {
        return actionBarSupported != null ? actionBarSupported : super.serverSupportsActionBar();
    }

    @Override
    public IPlatformVecAdapter getVecAdapter() {
        //a configured null is an answer ("this platform has no vec adapter"); an unset one is not
        return vecAdapterConfigured != null ? vecAdapter : super.getVecAdapter();
    }

    @Override
    public IPlatformChatAdapter getChatAdapter() {
        return chatAdapterConfigured != null ? chatAdapter : super.getChatAdapter();
    }

    @Override
    public void registerConfigTypes() {
        if (configTypesIgnored == null) {
            super.registerConfigTypes();
        }
    }

    @Override
    public void registerArgParsers() {
        if (configTypesIgnored == null) {
            super.registerArgParsers();
        }
    }

    @Override
    public CompletableFuture<Void> runOnMainThread(Runnable task) {
        if (mainThreadInline == null) {
            return super.runOnMainThread(task);
        }
        //inline: makes the first-tick legacy import deterministic in tests
        //(PlayerController.bootstrap blocks until the import + load finish)
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) {
        if (mainThreadInline == null) {
            return super.runOnMainThread(task);
        }
        //inline: tests have no server thread
        return CompletableFuture.completedFuture(task.get());
    }

    @Override
    public void shutdown(String reason) {
        if (recordingShutdowns == null) {
            super.shutdown(reason);
            return;
        }
        shutdownReasons.add(reason);
    }

    /** The chat adapter a lenient platform answers with: every question says "no opinion". */
    static IPlatformChatAdapter neutralChatAdapter() {
        return new IPlatformChatAdapter() {
            @Override
            public ITextMetrics getTextMetrics() {
                // Unmeasured text makes every layout helper hand its input straight back.
                return ITextMetrics.UNMEASURED;
            }

            @Override
            public List<FCommandSender> getBroadcastAudience() {
                return Collections.emptyList();
            }

            @Override
            public boolean supportsHover(String typeId) {
                // Stands in for "no platform has an opinion", not for a real platform's hover support.
                return true;
            }
        };
    }
}
