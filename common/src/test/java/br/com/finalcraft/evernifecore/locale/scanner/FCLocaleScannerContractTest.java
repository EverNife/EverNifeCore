package br.com.finalcraft.evernifecore.locale.scanner;

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
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link FCLocaleScanner}'s duplicate-key handling: two fields whose names differ only by case
 * collapse into a single registered message (the first one scanned wins, the second is discarded),
 * and the warning logged for it has to describe exactly that.
 */
public class FCLocaleScannerContractTest {

    @BeforeAll
    static void installTestPlatform() {
        TestPlatformFixture.ensureInstalled();
    }

    @TempDir
    Path tempDir;

    private String registeredPluginName;

    @AfterEach
    void teardown() {
        if (registeredPluginName != null) {
            ECPluginManager.removePluginData(registeredPluginName);
            registeredPluginName = null;
        }
    }

    /** Two Java fields ("title"/"Title") that normalize to the exact same scanner key. */
    public static class DuplicateKeyLocales {
        @FCLocale(text = "First message")
        public static LocaleMessage title;

        @FCLocale(text = "Second message")
        public static LocaleMessage Title;
    }

    @Test
    void duplicateKeyWarningDescribesWhatActuallyHappens() {
        List<String> capturedWarnings = new ArrayList<>();
        ECPluginData plugin = pluginDataWithCapturedLog("DuplicateKeyPlugin", capturedWarnings);

        List<LocaleMessageImp> scanned = FCLocaleScanner.scanForLocale(plugin, true, DuplicateKeyLocales.class);

        // the first field registered wins: both fields end up pointing at the very same message...
        assertSame(DuplicateKeyLocales.title, DuplicateKeyLocales.Title,
                "a case-only key collision must collapse into the SAME LocaleMessage instance");
        String text = ((LocaleMessageImp) DuplicateKeyLocales.title).getDefaultFancyText().toLegacyString();
        assertEquals("First message", text,
                "the FIRST field scanned must win; the second field's text must never be used");

        // ...but the logged warning has to say so. Today it claims the opposite ("Overriding last one!").
        assertEquals(1, capturedWarnings.size(), "exactly one duplicate-key warning must be logged");
        assertTrue(capturedWarnings.get(0).toLowerCase(Locale.ROOT).contains("first"),
                "the warning must describe reality (the FIRST message wins, the second is discarded), not: "
                        + capturedWarnings.get(0));
    }

    /** Root + one child, both using the new click()/clickType() attribute names. */
    public static class ClickVocabularyLocale {
        @FCLocale(text = "Click here", hover = "Info", click = "/give {player} diamond", clickType = ClickActionType.OPEN_URL,
                children = {
                        @FCLocale.Child(text = "child text", hover = "child hover", click = "/child cmd", clickType = ClickActionType.SUGGEST_COMMAND)
                })
        public static LocaleMessage msg;
    }

    @Test
    void clickAndClickTypeAttributesProduceTheSameResultAsTheOldRunCommandAndClickActionTypeAttributes() {
        ECPluginData plugin = pluginDataWithCapturedLog("ClickVocabularyPlugin", new ArrayList<>());

        List<LocaleMessageImp> scanned = FCLocaleScanner.scanForLocale(plugin, true, ClickVocabularyLocale.class);
        FancyFormatter formatter = (FancyFormatter) scanned.get(0).getDefaultFancyText();

        FancyText root = formatter.getFancyTextList().get(0);
        FancyText child = formatter.getFancyTextList().get(formatter.getFancyTextList().size() - 1);

        // captured by running the old runCommand()/clickActionType() attributes with the same
        // literal values before they were renamed - not deduced from reading the scanner's code.
        assertEquals("/give {player} diamond", root.getClickActionText());
        assertEquals(ClickActionType.OPEN_URL, root.getClickActionType());
        assertEquals("/child cmd", child.getClickActionText());
        assertEquals(ClickActionType.SUGGEST_COMMAND, child.getClickActionType());
    }

    private ECPluginData pluginDataWithCapturedLog(String pluginName, List<String> capturedWarnings) {
        IPlatform original = EverNifeCore.getPlatform();
        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class,
                new WarningCapturingPlatform(original, capturedWarnings));
        try {
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                    new FakePluginExtractor(pluginName, tempDir.resolve(pluginName).toFile()));
            registeredPluginName = pluginName;
            return ECPluginManager.getOrCreateECorePluginData(new Object());
        } finally {
            // the ECLogger created above already captured this adapter for good; restore the shared
            // platform right away so no other test in this JVM is affected.
            EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, original);
        }
    }

    /** Delegates everything to the currently-installed platform, except the log adapter it hands out. */
    private static final class WarningCapturingPlatform implements IPlatform {
        private final IPlatform delegate;
        private final List<String> warnings;

        WarningCapturingPlatform(IPlatform delegate, List<String> warnings) {
            this.delegate = delegate;
            this.warnings = warnings;
        }

        @Override
        public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {
            return new ILogAdapter() {
                @Override public void info(String string) { }
                @Override public void warning(String string) { warnings.add(string); }
                @Override public void severe(String string) { }
                @Override public void log(java.util.logging.Level level, String string) { }
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
        @Override public void sendActionBarMessage(FPlayer player, FancyText fancyText) { delegate.sendActionBarMessage(player, fancyText); }
        @Override public boolean serverSupportsActionBar() { return delegate.serverSupportsActionBar(); }
        @Override public IPlatformVecAdapter getVecAdapter() { return delegate.getVecAdapter(); }
        @Override public IPlatformChatAdapter getChatAdapter() { return delegate.getChatAdapter(); }
        @Override public CompletableFuture<Void> runOnMainThread(Runnable task) { return delegate.runOnMainThread(task); }
        @Override public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) { return delegate.runOnMainThread(task); }
        @Override public void registerConfigTypes() { delegate.registerConfigTypes(); }
        @Override public void registerArgParsers() { delegate.registerArgParsers(); }
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
