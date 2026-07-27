package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the single broadcast audience: a message broadcast as a raw {@link FancyText} and the same
 * message broadcast through a {@link LocaleMessage} must reach exactly the same recipients, and the
 * console has to be among them.
 */
@ECoreTest
public class BroadcastAudienceContractTest {


    @TempDir
    Path tempDir;

    private IPlatform installedBeforeTest;
    private String registeredPluginName;

    @AfterEach
    void restorePlatform() {
        if (installedBeforeTest != null) {
            EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, installedBeforeTest);
            installedBeforeTest = null;
        }
        if (registeredPluginName != null) {
            ECPluginManager.removePluginData(registeredPluginName);
            registeredPluginName = null;
        }
    }

    @Test
    void bothBroadcastRoutesReachTheSameAudienceAndItIncludesTheConsole() {
        TestCommandSender playerOne = new TestCommandSender("PlayerOne");
        TestCommandSender playerTwo = new TestCommandSender("PlayerTwo");
        TestCommandSender console = new TestCommandSender("CONSOLE");
        List<TestCommandSender> audience = Arrays.asList(playerOne, playerTwo, console);

        installBroadcastAudience(audience);

        FancyText.of("§aBroadcast").broadcast();
        List<String> reachedByFancyText = reached(audience);
        audience.forEach(TestCommandSender::clearMessages);

        LocaleMessageImp message = new LocaleMessageImp(pluginData("BroadcastAudiencePlugin"), "broadcast.line", false);
        message.addLocale("EN_US", new FancySegment("§aBroadcast"));
        message.broadcast();
        List<String> reachedByLocaleMessage = reached(audience);

        assertEquals(Arrays.asList("PlayerOne", "PlayerTwo", "CONSOLE"), reachedByFancyText,
                "FancyText.broadcast() must reach every recipient the chat adapter reports");
        assertEquals(reachedByFancyText, reachedByLocaleMessage,
                "LocaleMessage.broadcast() must reach exactly the same audience as FancyText.broadcast()");
        assertTrue(reachedByLocaleMessage.contains("CONSOLE"),
                "the broadcast audience includes the console");
    }

    private static List<String> reached(List<TestCommandSender> audience) {
        List<String> names = new ArrayList<>();
        for (TestCommandSender sender : audience) {
            if (!sender.getMessages().isEmpty()) {
                names.add(sender.getName());
            }
        }
        return names;
    }

    private void installBroadcastAudience(List<TestCommandSender> audience) {
        installedBeforeTest = EverNifeCore.getPlatform();
        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class,
                new BroadcastAudiencePlatform(installedBeforeTest, new ArrayList<>(audience)));
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                new FakePluginExtractor(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredPluginName = pluginName;
        return ECPluginManager.getOrCreateECorePluginData(new Object());
    }

    /** Delegates everything to the currently-installed platform, except the broadcast audience. */
    private static final class BroadcastAudiencePlatform implements IPlatform {
        private final IPlatform delegate;
        private final List<FCommandSender> audience;

        BroadcastAudiencePlatform(IPlatform delegate, List<? extends FCommandSender> audience) {
            this.delegate = delegate;
            this.audience = new ArrayList<>(audience);
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
                public List<FCommandSender> getBroadcastAudience() {
                    return audience;
                }

                @Override
                public boolean supportsHover(String typeId) {
                    return true;
                }
            };
        }

        @Override public String getPlatformProviderId() { return delegate.getPlatformProviderId(); }
        @Override public List<FPlayer> getOnlinePlayers() { return delegate.getOnlinePlayers(); }
        @Override public FPlayer getPlayer(String playerName) { return delegate.getPlayer(playerName); }
        @Override public FPlayer getPlayer(UUID playerUuid) { return delegate.getPlayer(playerUuid); }
        @Override public boolean isPluginLoaded(String pluginName) { return delegate.isPluginLoaded(pluginName); }
        @Override public boolean makeConsoleExecuteCommand(String command) { return delegate.makeConsoleExecuteCommand(command); }
        @Override public boolean makePlayerExecuteCommand(FCommandSender sender, String command) { return delegate.makePlayerExecuteCommand(sender, command); }
        @Override public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand) { return delegate.registerCommand(finalCMDPluginCommand); }
        @Override public void unregisterCommand(String commandName, ECPluginData notifyPlugin) { delegate.unregisterCommand(commandName, notifyPlugin); }
        @Override public void registerECListener(ECPluginData ecPluginData, ECListener listener) { delegate.registerECListener(ecPluginData, listener); }
        @Override public void unregisterECListener(ECListener listener) { delegate.unregisterECListener(listener); }
        @Override public boolean isPAPIPresent() { return delegate.isPAPIPresent(); }
        @Override public String parse(@Nullable FPlayer player, @Nonnull String text) { return delegate.parse(player, text); }
        @Override public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType) { return delegate.createPlaceholderIntegration(plugin, pluginBaseID, playerDataType); }
        @Override public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) { return delegate.createLogAdapterFor(ecPluginData); }
        @Override public void sendActionBarMessage(FPlayer player, FancyText fancyText) { delegate.sendActionBarMessage(player, fancyText); }
        @Override public boolean serverSupportsActionBar() { return delegate.serverSupportsActionBar(); }
        @Override public IPlatformVecAdapter getVecAdapter() { return delegate.getVecAdapter(); }
        @Override public CompletableFuture<Void> runOnMainThread(Runnable task) { return delegate.runOnMainThread(task); }
        @Override public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) { return delegate.runOnMainThread(task); }
        @Override public void registerConfigTypes() { delegate.registerConfigTypes(); }
        @Override public void registerArgParsers() { delegate.registerArgParsers(); }
        @Override public void shutdown(String reason) { delegate.shutdown(reason); }
    }

    private static final class FakePluginExtractor implements IECPluginExtractor {
        private final String pluginName;
        private final File dataFolder;

        FakePluginExtractor(String pluginName, File dataFolder) {
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getPluginName(Object javaPlugin) {
            return pluginName;
        }

        @Override
        public boolean isJavaPlugin(Object plugin) {
            return true;
        }

        @Override
        public Object getProvidingPlugin(Class<?> clazz) {
            return null;
        }

        @Override
        public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
            return new FakeMetaInfo(javaPlugin, pluginName, dataFolder);
        }
    }

    private static final class FakeMetaInfo implements IPluginMetaInfo {
        private final Object plugin;
        private final String pluginName;
        private final File dataFolder;

        FakeMetaInfo(Object plugin, String pluginName, File dataFolder) {
            this.plugin = plugin;
            this.pluginName = pluginName;
            this.dataFolder = dataFolder;
        }

        @Override
        public String getName() {
            return pluginName;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getAuthor() {
            return "Petrus";
        }

        @Override
        public String getGroup() {
            return "br.com.finalcraft";
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }

        @Override
        public Object getDelegate() {
            return plugin;
        }
    }
}
