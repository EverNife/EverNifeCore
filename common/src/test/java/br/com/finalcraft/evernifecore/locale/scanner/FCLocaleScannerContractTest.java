package br.com.finalcraft.evernifecore.locale.scanner;

import br.com.finalcraft.evernifecore.testing.Plugins;
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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * Pins {@link FCLocaleScanner}'s duplicate-key handling: colliding fields collapse into a single
 * registered message (the first one scanned wins, the second is discarded), and the report logged for
 * it has to describe exactly that and name both culprits - whether the two fields sit in the same
 * class or in two classes that merely share a simple name.
 */
@ECoreTest
public class FCLocaleScannerContractTest {


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
    void duplicateKeyReportDescribesWhatActuallyHappensAndNamesBothFields() {
        List<String> capturedSevere = new ArrayList<>();
        ECPluginData plugin = pluginDataWithCapturedLog("DuplicateKeyPlugin", new ArrayList<>(), capturedSevere);

        List<LocaleMessageImp> scanned = FCLocaleScanner.scanForLocale(plugin, true, DuplicateKeyLocales.class);

        // the first field registered wins: both fields end up pointing at the very same message...
        assertSame(DuplicateKeyLocales.title, DuplicateKeyLocales.Title,
                "a case-only key collision must collapse into the SAME LocaleMessage instance");
        String text = ((LocaleMessageImp) DuplicateKeyLocales.title).getDefaultFancyText().toLegacyString();
        assertEquals("First message", text,
                "the FIRST field scanned must win; the second field's text must never be used");

        // ...and the report has to say so, at a level that is not lost in a boot log, naming BOTH
        // culprits so the reader does not have to go looking for the other one.
        assertEquals(1, capturedSevere.size(), "exactly one duplicate-key report must be logged: " + capturedSevere);
        String report = capturedSevere.get(0);
        assertTrue(report.toLowerCase(Locale.ROOT).contains("first"),
                "the report must describe reality (the FIRST message wins, the second is discarded), not: " + report);
        assertTrue(report.contains(DuplicateKeyLocales.class.getName() + "#title"), "must name the winning field: " + report);
        assertTrue(report.contains(DuplicateKeyLocales.class.getName() + "#Title"), "must name the losing field: " + report);
    }

    @Test
    void twoClassesWithTheSameSimpleNameInDifferentPackagesAreReported() {
        List<String> capturedSevere = new ArrayList<>();
        ECPluginData plugin = pluginDataWithCapturedLog("SameSimpleNamePlugin", new ArrayList<>(), capturedSevere);

        // scanned separately, exactly as a plugin registering two unrelated locale holders would
        FCLocaleScanner.scanForLocale(plugin, true, br.com.finalcraft.evernifecore.locale.scanner.alpha.SharedSimpleName.class);
        FCLocaleScanner.scanForLocale(plugin, true, br.com.finalcraft.evernifecore.locale.scanner.beta.SharedSimpleName.class);

        assertSame(br.com.finalcraft.evernifecore.locale.scanner.alpha.SharedSimpleName.GREETING,
                br.com.finalcraft.evernifecore.locale.scanner.beta.SharedSimpleName.GREETING,
                "the key is built from the SIMPLE class name, so both fields share one message");
        assertEquals("Greeting from alpha",
                ((LocaleMessageImp) br.com.finalcraft.evernifecore.locale.scanner.beta.SharedSimpleName.GREETING)
                        .getDefaultFancyText().toLegacyString(),
                "the FIRST class scanned must keep winning; only the reporting is new");

        assertEquals(1, capturedSevere.size(), "a cross-class collision must be reported once: " + capturedSevere);
        String report = capturedSevere.get(0);
        assertTrue(report.contains("br.com.finalcraft.evernifecore.locale.scanner.alpha.SharedSimpleName#GREETING"),
                "must name the field that won: " + report);
        assertTrue(report.contains("br.com.finalcraft.evernifecore.locale.scanner.beta.SharedSimpleName#GREETING"),
                "must name the field that lost: " + report);
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
        return pluginDataWithCapturedLog(pluginName, capturedWarnings, new ArrayList<>());
    }

    private ECPluginData pluginDataWithCapturedLog(String pluginName, List<String> capturedWarnings, List<String> capturedSevere) {
        IPlatform original = EverNifeCore.getPlatform();
        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class,
                new WarningCapturingPlatform(original, capturedWarnings, capturedSevere));
        try {
            EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                    Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
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
        private final List<String> severe;

        WarningCapturingPlatform(IPlatform delegate, List<String> warnings, List<String> severe) {
            this.delegate = delegate;
            this.warnings = warnings;
            this.severe = severe;
        }

        @Override
        public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) {
            return new ILogAdapter() {
                @Override public void info(String string) { }
                @Override public void warning(String string) { warnings.add(string); }
                @Override public void severe(String string) { severe.add(string); }
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
        @Override public CompletableFuture<Void> runOnMainThreadNextTick(Runnable task) { return delegate.runOnMainThreadNextTick(task); }
        @Override public <T> CompletableFuture<T> runOnMainThreadNextTick(Supplier<T> task) { return delegate.runOnMainThreadNextTick(task); }
        @Override public void registerConfigTypes() { delegate.registerConfigTypes(); }
        @Override public void registerArgParsers() { delegate.registerArgParsers(); }
        @Override public void shutdown(String reason) { delegate.shutdown(reason); }
    }


}
