package br.com.finalcraft.evernifecore.finalcommandsystemtests.harness;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A headless {@link IPlatform} that behaves like a no-op platform (same defaults as
 * {@code TestPlatformFixture}'s), except it captures every {@link #registerCommand} /
 * {@link #unregisterCommand} call so tests can assert on the registration/dispatch flow
 * without a real Bukkit/Hytale server.
 * <p>
 * Instance state (not static): install a fresh one per test class so command labels used by one
 * test class can never leak into another.
 */
public class CommandCapturePlatform implements IPlatform {

    private final Map<String, FinalCMDPluginCommand> capturedByLabel = new LinkedHashMap<>();
    private final List<String> unregisteredLabels = new ArrayList<>();
    private final List<FinalCMDPluginCommand> registrationOrder = new ArrayList<>();
    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> shutdownReasons = new ArrayList<>();
    private boolean forceRegisterFailure = false;

    /** When {@code true}, every subsequent {@link #registerCommand} call rejects (returns false) without capturing anything - simulates a platform-level registration failure (RG8). */
    public void setForceRegisterFailure(boolean forceRegisterFailure) {
        this.forceRegisterFailure = forceRegisterFailure;
    }

    public @Nullable FinalCMDPluginCommand getCaptured(String label) {
        return capturedByLabel.get(label);
    }

    public List<String> getUnregisteredLabels() {
        return unregisteredLabels;
    }

    /** Every {@link FinalCMDPluginCommand} this platform captured, in the order {@link #registerCommand} was called. */
    public List<FinalCMDPluginCommand> registrationOrder() {
        return registrationOrder;
    }

    /** Every {@code info}-level message logged through an {@link ILogAdapter} this platform created. */
    public List<String> getInfoMessages() {
        return infoMessages;
    }

    /** Every reason passed to {@link #shutdown(String)} - a real shutdown would kill the test JVM. */
    public List<String> getShutdownReasons() {
        return shutdownReasons;
    }

    public void reset() {
        capturedByLabel.clear();
        unregisteredLabels.clear();
        registrationOrder.clear();
        infoMessages.clear();
        shutdownReasons.clear();
    }

    @Override
    public String getPlatformProviderId() {
        return "test";
    }

    @Override
    public List<FPlayer> getOnlinePlayers() {
        return Collections.emptyList();
    }

    @Override
    public FPlayer getPlayer(String playerName) {
        return null;
    }

    @Override
    public FPlayer getPlayer(UUID playerUuid) {
        return null;
    }

    @Override
    public boolean isPluginLoaded(String pluginName) {
        return false;
    }

    @Override
    public boolean makeConsoleExecuteCommand(String command) {
        return false;
    }

    @Override
    public boolean makePlayerExecuteCommand(FCommandSender sender, String command) {
        return false;
    }

    @Override
    public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand) {
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
        unregisteredLabels.add(commandName);
        capturedByLabel.remove(commandName);
    }

    @Override
    public void registerECListener(ECPluginData ecPluginData, ECListener listener) {
    }

    @Override
    public void unregisterECListener(ECListener listener) {
    }

    @Override
    public boolean isPAPIPresent() {
        return false;
    }

    @Override
    public String parse(@Nullable FPlayer player, @Nonnull String text) {
        return text;
    }

    @Override
    public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType) {
        return null;
    }

    @Override
    public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {
        return new ILogAdapter() {
            @Override
            public void info(String string) {
                infoMessages.add(string);
                System.out.println(string);
            }

            @Override
            public void warning(String string) {
                System.out.println("[WARN] " + string);
            }

            @Override
            public void severe(String string) {
                System.out.println("[SEVERE] " + string);
            }

            @Override
            public void log(java.util.logging.Level level, String string) {
                System.out.println("[" + level + "] " + string);
            }
        };
    }

    @Override
    public void sendActionBarMessage(FPlayer player, FancyText fancyText) {
    }

    @Override
    public boolean serverSupportsActionBar() {
        return false;
    }

    @Override
    public IPlatformVecAdapter getVecAdapter() {
        return null;
    }

    @Override
    public IPlatformChatAdapter getChatAdapter() {
        return new IPlatformChatAdapter() {
            @Override
            public String alignCenter(String stringToAlign) {
                return stringToAlign;
            }

            @Override
            public String alignCenter(String stringToAlign, String borderFill) {
                return stringToAlign;
            }

            @Override
            public String straightLineOf(String string) {
                return string;
            }

            @Override
            public void broadcast(FancyText fancyText) {
            }
        };
    }

    @Override
    public void registerConfigTypes() {
    }

    @Override
    public void registerArgParsers() {
    }

    @Override
    public CompletableFuture<Void> runOnMainThread(Runnable task) {
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) {
        return CompletableFuture.completedFuture(task.get());
    }

    @Override
    public void shutdown(String reason) {
        shutdownReasons.add(reason);   //a real shutdown would kill the test JVM
    }
}
