package br.com.finalcraft.evernifecore.fancytext;

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
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message declares its keys exactly as written: a key that still carries a delimiter is registered
 * literally - so it never matches anything - and is warned about once, which is the only thing
 * standing between a caller who did not migrate and a message that silently stops resolving.
 */
@ECoreTest
public class MessagePlaceholdersContractTest {


    @TempDir
    Path tempDir;

    @Test
    void aKeyWrittenWithItsDelimitersIsRegisteredLiterallyAndNeverMatches() {
        MessagePlaceholders placeholders = new MessagePlaceholders();
        placeholders.declare("%saldo%", "", context -> 10);

        assertTrue(placeholders.getProvider().getParserMap().containsKey("%saldo%"),
                "the key must be registered exactly as written: " + placeholders.getProvider().getParserMap().keySet());
        assertEquals("${saldo}", placeholders.apply("${saldo}", RenderContext.empty()),
                "a delimited key must not answer for the bare one");
    }

    @Test
    void aBareKeyResolvesTheDollarCurlyClosureOnly() {
        MessagePlaceholders placeholders = new MessagePlaceholders();
        placeholders.declare("saldo", "", context -> 10);

        assertEquals("10 and %saldo%", placeholders.apply("${saldo} and %saldo%", RenderContext.empty()));
    }

    @Test
    void aKeyCitedTwiceInOneRenderIsComputedOnce() {
        AtomicInteger calls = new AtomicInteger();
        MessagePlaceholders placeholders = new MessagePlaceholders();
        placeholders.declare("saldo", "", context -> calls.incrementAndGet());

        RenderContext oneRender = RenderContext.empty();
        assertEquals("1 1", placeholders.apply("${saldo} ${SALDO}", oneRender));
        assertEquals(1, calls.get(), "the same key in one render must cost exactly one resolution");
    }

    // The percent form is PlaceholderAPI's, and the engine stopped reading it: a message declaring
    // "saldo" resolves ${saldo} and leaves %saldo% for whoever owns that closure.
    @Test
    void thePercentFormIsNoLongerResolvedByTheMessageEngine() {
        assertEquals("10 e %saldo%", FancyText.of("${saldo} e %saldo%")
                .addPlaceholder("saldo", 10)
                .toLegacyString(RenderContext.empty()));
    }

    // The description is what an integrating plugin lists back to the user, so it has to survive the
    // trip from the message down to its provider.
    @Test
    void describeAllOfAMessageListsEveryDescribedParser() {
        FancyText message = FancyText.of("${saldo} ${banco}")
                .addParser("saldo", "The player's balance", context -> 10)
                .addParser("banco", "The player's bank balance", context -> 20);

        Map<String, String> described = message.getPlaceholderProvider().describeAll();

        assertEquals(2, described.size(), "both parsers must be listed: " + described);
        assertEquals("The player's balance", described.get("saldo"));
        assertEquals("The player's bank balance", described.get("banco"));
    }

    @Test
    void declaringADelimitedKeyWarnsOncePerKey() {
        List<String> warnings = new ArrayList<>();
        withWarningsCapturedInto(warnings, () -> {
            String key = "%" + UUID.randomUUID() + "%";   // never warned about by another test

            new MessagePlaceholders().declare(key, "", context -> 1);
            new MessagePlaceholders().declare(key, "", context -> 2);

            assertEquals(1, warnings.size(), "the same key must be reported once, not once per call site: " + warnings);
            assertTrue(warnings.get(0).contains(key), "the warning must name the key as written: " + warnings.get(0));
            assertTrue(warnings.get(0).contains("${" + key.substring(1, key.length() - 1) + "}"),
                    "the warning must suggest the bare key inside the canonical closure: " + warnings.get(0));
        });
    }

    @Test
    void aBareKeyIsNotWarnedAbout() {
        List<String> warnings = new ArrayList<>();
        withWarningsCapturedInto(warnings, () -> {
            new MessagePlaceholders().declare("saldo_" + UUID.randomUUID(), "", context -> 1);
            assertTrue(warnings.isEmpty(), "a properly declared key must say nothing: " + warnings);
        });
    }

    // The warning goes through EverNifeCore's own logger, so the only way to observe it is to own
    // that bootstrap for the duration of the call - swapping the platform alone would not do it, as
    // an ECPluginData freezes its log adapter when it is built.
    private void withWarningsCapturedInto(List<String> warnings, Runnable body) {
        IPlatform originalPlatform = EverNifeCore.getPlatform();
        ECPluginData originalCoreData = EverNifeCore.getEcPluginData();
        String pluginName = "MessagePlaceholdersWarnings_" + UUID.randomUUID();

        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class,
                new WarningCapturingPlatform(originalPlatform, warnings));
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        try {
            EverNifeCore.instance.onLoaderInstantiate(ECPluginManager.getOrCreateECorePluginData(new Object()));
            body.run();
        } finally {
            setCoreEcPluginData(originalCoreData);
            ECPluginManager.removePluginData(pluginName);
            EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, originalPlatform);
        }
    }

    // onLoaderInstantiate cannot put back a core that had none, and leaving this test's throwaway
    // plugin installed would hand it every later log line in this JVM.
    private static void setCoreEcPluginData(@Nullable ECPluginData ecPluginData) {
        try {
            Field field = EverNifeCore.class.getDeclaredField("ecPluginData");
            field.setAccessible(true);
            field.set(EverNifeCore.instance, ecPluginData);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not restore EverNifeCore's plugin data", e);
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
        @Override public CompletableFuture<Void> runOnMainThreadNextTick(Runnable task) { return delegate.runOnMainThreadNextTick(task); }
        @Override public <T> CompletableFuture<T> runOnMainThreadNextTick(Supplier<T> task) { return delegate.runOnMainThreadNextTick(task); }
        @Override public void registerConfigTypes() { delegate.registerConfigTypes(); }
        @Override public void registerArgParsers() { delegate.registerArgParsers(); }
        @Override public void shutdown(String reason) { delegate.shutdown(reason); }
    }


}
