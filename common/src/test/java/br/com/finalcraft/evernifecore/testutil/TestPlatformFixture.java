package br.com.finalcraft.evernifecore.testutil;

import br.com.finalcraft.evernifecore.EverNifeCore;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Registers a no-op {@link IPlatform} in the ECProviders so that common code with
 * platform hooks (e.g. config-type registration via {@code registerConfigTypes()}) can
 * run under plain JUnit, without a Bukkit/Hytale runtime.
 *
 * <p>Call {@link #ensureInstalled()} in a {@code @BeforeAll} of any test that writes
 * through the config engine or touches platform-dependent paths.</p>
 */
public final class TestPlatformFixture {

    private static volatile boolean installed = false;

    private TestPlatformFixture() {
    }

    public static synchronized void ensureInstalled() {
        //the background flush tick would race scripted repositories and save-count assertions;
        //tests drive flushes explicitly through flushAll()
        System.setProperty("evernifecore.playerdata.periodic-flush", "false");
        if (installed) {
            return;
        }
        try {
            EverNifeCore.getPlatform();   // some other fixture already registered one
            installed = true;
            return;
        } catch (Throwable noPlatformYet) {
            // expected on a freshly started test JVM
        }
        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, new NoopPlatform());
        installed = true;
    }

    private static final class NoopPlatform implements IPlatform {

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
            return false;
        }

        @Override
        public void unregisterCommand(String commandName, ECPluginData notifyPlugin) {
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
            return null;
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
            return null;
        }

        @Override
        public void registerConfigTypes() {
        }

        @Override
        public void registerArgParsers() {
        }

        @Override
        public void runOnFirstTick(Runnable runnable) {
            //inline: makes the first-tick legacy import deterministic in tests
            //(PlayerController.bootstrap blocks until the import + load finish)
            runnable.run();
        }

        @Override
        public void runOnMainThread(Runnable runnable) {
            //inline: tests have no server thread
            runnable.run();
        }
    }
}
