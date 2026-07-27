package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the SendCustom/SendCustomComplex pipeline: getFancyText, used for a preview/inspection, and
 * send, used to actually deliver the message, must agree on what a decorated {@link LocaleMessage}
 * contains.
 */
@ECoreTest
public class LocaleMessageSendContractTest {


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

    // An appended message renders EVERY piece of the chain, and getFancyText(sender) must report
    // the same chain instead of only the last message appended.
    @Test
    void concatGetFancyTextMatchesWhatSendActuallyRenders() {
        ECPluginData plugin = pluginData("ConcatBugPlugin");

        LocaleMessageImp first = new LocaleMessageImp(plugin, "concat.first", false);
        first.addLocale("EN_US", new FancySegment("Hello "));

        LocaleMessageImp second = new LocaleMessageImp(plugin, "concat.second", false);
        second.addLocale("EN_US", new FancySegment("World"));

        TestCommandSender console = new TestCommandSender("CONSOLE");
        ILocaleMessageBase combined = first.append(second);

        combined.send(console);
        String actuallySent = console.getMessages().get(0);

        String previewed = combined.getFancyText(console).toLegacyString();

        assertEquals(actuallySent, previewed,
                "append(...).getFancyText(sender) must describe exactly what send(sender) delivers");
    }

    // A raw FancyText is a chain piece like any other: appending one renders it as part of the whole
    // message, and the preview still describes exactly what was delivered.
    @Test
    void appendedFancyTextIsRenderedAsPartOfTheChain() {
        ECPluginData plugin = pluginData("AppendFancyTextPlugin");

        LocaleMessageImp greeting = new LocaleMessageImp(plugin, "append.greeting", false);
        greeting.addLocale("EN_US", new FancySegment("Hello "));

        TestCommandSender console = new TestCommandSender("CONSOLE");
        FancyText appended = FancyText.of("World");
        ILocaleMessageBase combined = greeting.append(appended).append("!");

        // The chain snapshots what it was given, so a later mutation of the caller's own instance
        // must not show up in what gets sent.
        appended.setText("MUTATED");

        combined.send(console);
        String actuallySent = console.getMessages().get(0);

        assertEquals("Hello World!", actuallySent);
        assertEquals(actuallySent, combined.getFancyText(console).toLegacyString(),
                "append(fancyText).getFancyText(sender) must describe exactly what send(sender) delivers");
    }

    // A Function<PlayerData,Object> placeholder can only be evaluated when the recipient has
    // PlayerData; for any other FCommandSender the token must stay as written rather than fall
    // through to String.valueOf(theFunctionItself).
    @Test
    void perPlayerPlaceholderIsNotLeakedAsLambdaToStringForANonPlayerSender() {
        LocaleMessageImp message = new LocaleMessageImp(pluginData("LambdaLeakPlugin"), "lambda.leak", false);
        message.addLocale("EN_US", new FancySegment("Hello ${name}"));

        TestCommandSender console = new TestCommandSender("CONSOLE");
        Function<PlayerData, Object> perPlayerName = (PlayerData playerData) -> playerData.getUniqueId();

        message.addPlaceholder("name", perPlayerName).send(console);

        assertFalse(console.getMessages().get(0).contains("Lambda"),
                "a Function placeholder must not leak its own toString() to a non-player recipient: "
                        + console.getMessages().get(0));
        assertEquals("Hello ${name}", console.getMessages().get(0),
                "with no PlayerData to resolve against, the token stays exactly as written");
    }

    // A message nobody registered a language for used to answer null, which the send path then
    // copy()'d - so the message that was merely undefined took the command down with it.
    @Test
    void aLocaleWithNoRegisteredLanguageRendersItsOwnKeyInsteadOfFailing() {
        LocaleMessageImp undefined = new LocaleMessageImp(pluginData("UndefinedLocalePlugin"), "undefined.key", false);

        FancyText fallback = assertDoesNotThrow(undefined::getDefaultFancyText);
        assertTrue(fallback.toPlainText().contains("undefined.key"),
                "the fallback must name the key it stands for: " + fallback.toPlainText());
        assertFalse(undefined.isDefined(), "a message with no registered language is not defined");

        TestCommandSender console = new TestCommandSender("CONSOLE");
        assertDoesNotThrow(() -> undefined.send(console));
        assertTrue(console.getMessages().get(0).contains("undefined.key"),
                "the recipient must see which message was left undefined: " + console.getMessages());
    }

    @Test
    void aLocaleWithARegisteredLanguageIsDefined() {
        LocaleMessageImp defined = new LocaleMessageImp(pluginData("DefinedLocalePlugin"), "defined.key", false);
        defined.addLocale("EN_US", new FancySegment("Hello"));

        assertTrue(defined.isDefined());
        assertEquals("Hello", defined.getDefaultFancyText().toPlainText());
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                new FakePluginExtractor(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredPluginName = pluginName;
        return ECPluginManager.getOrCreateECorePluginData(new Object());
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
