package br.com.finalcraft.evernifecore.dynamiccommand;

import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.misc.CMDECDynamicCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A link belongs to the plugin that made it: it stops working when that plugin leaves, and the core texts its action
 * sends speak that plugin's language, not the one of the core that dispatches {@code /ecdcmd}.
 */
class DynamicCommandOwnerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private ECPluginData shop;
    private FinalCMDPluginCommand ecdcmd;

    @BeforeEach
    void open() {
        harness = Commands.harness("LinkHost", tempDir.resolve("host"));
        ECPluginData core = harness.withSeparateCore(tempDir.resolve("core"));
        Plugins.setLanguage(harness.ecPluginData, LocaleType.PT_BR);
        Plugins.setLanguage(core, LocaleType.PT_BR);
        shop = Plugins.fakeOther("LinkShop", tempDir.resolve("shop").toFile());
        Plugins.setLanguage(shop, LocaleType.EN_US);
        ecdcmd = harness.register(CMDECDynamicCommand.class);
        //the core's boot owns the command's texts (CORE_LOCALE_CLASSES): loaded last, as on a server
        FCLocaleManager.loadLocale(core, CMDECDynamicCommand.class);
    }

    @AfterEach
    void close() {
        Plugins.forget("LinkShop");
        harness.close();
    }

    @Test
    void theLinkOfAPluginThatLeftAnswersItHasNoCommandAndRunsNothing() {
        AtomicBoolean ran = new AtomicBoolean();
        String link = DynamicCommand.builder(shop).setAction(context -> ran.set(true)).createDynamicCommand().scheduleAndReturnCommandString();
        TestFPlayerSender steve = Senders.player("Steve");

        ECPluginManager.removePluginData("LinkShop");
        harness.dispatch(ecdcmd, steve, link.substring("/ecdcmd ".length()));

        assertFalse(ran.get(), "the action of a plugin that left still ran");
        assertTrue(steve.anyMessageContains("UUID"), steve.getMessages().toString());
    }

    @Test
    void aCoreTextSentByTheActionSpeaksTheLanguageOfThePluginThatMadeTheLink() {
        String link = DynamicCommand.builder(shop)
                .setAction(context -> FCMessageUtil.pluginHasBeenReloaded(context.getSender(), "LinkShop"))
                .createDynamicCommand().scheduleAndReturnCommandString();
        TestFPlayerSender steve = Senders.player("Steve");

        harness.dispatch(ecdcmd, steve, link.substring("/ecdcmd ".length()));

        assertTrue(steve.anyMessageContains("has been reloaded"), steve.getMessages().toString());
    }
}
