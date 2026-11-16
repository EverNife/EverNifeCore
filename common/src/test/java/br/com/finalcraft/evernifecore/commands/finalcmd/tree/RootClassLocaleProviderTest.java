package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A command class one plugin ships (a library of ready-made commands) and other plugins register as their root: its
 * static texts belong to the jar that ships it, so every registration finds the same message, owned by that jar.
 */
class RootClassLocaleProviderTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @FinalCMD(aliases = "librarycmd")
    public static class Library_Cmd {
        @FCLocale(lang = LocaleType.EN_US, text = "Shipped by the library.")
        static LocaleMessage SHIPPED;

        public void onCommand(FCommandSender sender) {
        }
    }

    @BeforeEach
    void open() {
        harness = Commands.harness("RootLocaleHost", tempDir.resolve("host"));
    }

    @AfterEach
    void close() {
        harness.close();
    }

    @Test
    void twoPluginsRegisteringAThirdPluginsClassAsRootLeaveItsTextWithTheProvider() {
        Plugins.Routing routing = Plugins.routing()
                .plugin("ShopA", tempDir.resolve("a").toFile(), "nothing.of.shop.a")
                .plugin("ShopB", tempDir.resolve("b").toFile(), "nothing.of.shop.b")
                .plugin("CmdLibrary", tempDir.resolve("lib").toFile(), Library_Cmd.class.getName());
        harness.withPluginExtractor(routing);
        ECPluginData shopA = ECPluginManager.getOrCreateECorePluginData(routing.getToken("ShopA"));
        ECPluginData shopB = ECPluginManager.getOrCreateECorePluginData(routing.getToken("ShopB"));
        try {
            FinalCMDManager.registerCommand(shopA, new Library_Cmd());
            LocaleMessage first = Library_Cmd.SHIPPED;
            FinalCMDManager.registerCommand(shopB, new Library_Cmd());

            assertEquals("CmdLibrary", ((LocaleMessageImp) Library_Cmd.SHIPPED).getPlugin().getMetaInfo().getName(),
                    "the text was loaded into a plugin that only registered the class");
            assertSame(first, Library_Cmd.SHIPPED, "the second registrant replaced the first one's message");
        } finally {
            FinalCMDManager.unregisterAllCommands(shopA);
            FinalCMDManager.unregisterAllCommands(shopB);
            Plugins.forget("ShopA");
            Plugins.forget("ShopB");
            Plugins.forget("CmdLibrary");
        }
    }
}
