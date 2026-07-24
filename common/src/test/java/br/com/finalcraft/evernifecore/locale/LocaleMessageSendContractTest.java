package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestCommandSender;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testutil.TestPlatformFixture;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Proves confirmed bugs in the SendCustom/SendCustomComplex pipeline: two independent code paths
 * (getFancyText, used for a preview/inspection, and send, used to actually deliver the message)
 * disagree on what a decorated {@link LocaleMessage} actually contains.
 */
public class LocaleMessageSendContractTest {

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

    // SendCustomComplex overrides send(...) to render EVERY concatenated LocaleMessage, but inherits
    // getFancyText(sender) from SendCustom unmodified - which only ever looks at its OWN
    // localeMessage (the last one concat()-ed in), silently dropping every earlier piece.
    @Test
    @Tag("known-bug")
    void concatGetFancyTextMatchesWhatSendActuallyRenders() {
        ECPluginData plugin = pluginData("ConcatBugPlugin");

        LocaleMessageImp first = new LocaleMessageImp(plugin, "concat.first", false);
        first.addLocale("EN_US", new FancySegment("Hello "));

        LocaleMessageImp second = new LocaleMessageImp(plugin, "concat.second", false);
        second.addLocale("EN_US", new FancySegment("World"));

        TestCommandSender console = new TestCommandSender("CONSOLE");
        SendCustom combined = first.concat(second);

        combined.send(console);
        String actuallySent = console.getMessages().get(0);

        String previewed = FCColorUtil.componentToString(combined.getFancyText(console).toComponent());

        assertEquals(actuallySent, previewed,
                "concat(...).getFancyText(sender) must describe exactly what send(sender) delivers");
    }

    // FancyTextManager.send() only evaluates a Function<PlayerData,Object> placeholder when the
    // recipient IS a player; for any other FCommandSender it falls through to
    // String.valueOf(theFunctionItself), leaking the lambda's toString() into the sent message.
    @Test
    @Tag("known-bug")
    void perPlayerPlaceholderIsNotLeakedAsLambdaToStringForANonPlayerSender() {
        LocaleMessageImp message = new LocaleMessageImp(pluginData("LambdaLeakPlugin"), "lambda.leak", false);
        message.addLocale("EN_US", new FancySegment("Hello {name}"));

        TestCommandSender console = new TestCommandSender("CONSOLE");
        Function<PlayerData, Object> perPlayerName = (PlayerData playerData) -> playerData.getUniqueId();

        message.addPlaceholder("{name}", perPlayerName).send(console);

        assertFalse(console.getMessages().get(0).contains("Lambda"),
                "a Function placeholder must not leak its own toString() to a non-player recipient: "
                        + console.getMessages().get(0));
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
