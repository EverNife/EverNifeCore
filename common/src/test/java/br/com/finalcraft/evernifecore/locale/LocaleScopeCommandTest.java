package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.command.AuditCommandNode;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Locales;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plugin in English on a server whose core speaks Portuguese: every core text inside the plugin's command - a mounted
 * core node, a core parser's refusal, the help, a reply after a future - speaks English, while the core's own command,
 * another plugin's message and a player's own choice keep theirs.
 */
class LocaleScopeCommandTest {

    private static final String BASE = "awshop.command.awshop.log";

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private ECPluginData core;
    private ECStorage storage;
    private AuditLog log;
    private FinalCMDPluginCommand awshop;

    @FinalCMD(aliases = "awshop")
    public static class AwShop_Cmd {
        @FinalCMD.Node(subcmd = "log", permission = BASE)
        final AuditCommandNode log;

        AwShop_Cmd(AuditCommandNode log) {
            this.log = log;
        }
    }

    @FinalCMD(aliases = "corecount")
    public static class CoreCount_Cmd {
        public void onCommand(FCommandSender sender, @Arg("<amount>") Integer amount) {
        }
    }

    @BeforeEach
    void open() throws Exception {
        harness = Commands.harness("AwShop", tempDir.resolve("awshop"));
        core = harness.withSeparateCore(tempDir.resolve("core"));
        Plugins.setLanguage(harness.ecPluginData, LocaleType.EN_US);
        Plugins.setLanguage(core, LocaleType.PT_BR);
        ScriptedStorage backend = ScriptedStorage.inMemory();
        backend.init().get(5, TimeUnit.SECONDS);
        storage = backend.asECStorage();
        log = AuditLog.of(harness.ecPluginData, storage);
        awshop = harness.register(new AwShop_Cmd(new AuditCommandNode(log, BASE)));
        //the core's boot owns the node's texts (CORE_LOCALE_CLASSES), whoever mounts it: loaded last, as on a server
        FCLocaleManager.loadLocale(core, ClassicPageTheme.class, PVExtraMessages.class, AuditCommandNode.class);
    }

    @AfterEach
    void close() throws Exception {
        AuditLog.closeOwnedBy(harness.ecPluginData);
        storage.close().get(5, TimeUnit.SECONDS);
        harness.close();
    }

    private static TestFPlayerSender viewer(TestFPlayerSender player) {
        return player.grant(BASE).grant(BASE + ".view");
    }

    @Test
    void aCoreNodeMountedByAnEnglishPluginAnswersInEnglish() {
        TestFPlayerSender steve = viewer(Senders.player("Steve"));

        harness.dispatch(awshop, steve, "log --kind nonsense");

        assertTrue(steve.anyMessageContains("No audit kind"), steve.getMessages().toString());
        assertNull(LocaleScope.current(), "the dispatch leaves no scope behind on the thread");
    }

    @Test
    void aCoreParserRefusalInsideThePluginsCommandIsInEnglish() {
        TestFPlayerSender steve = viewer(Senders.player("Steve"));

        harness.dispatch(awshop, steve, "log --page abc");

        assertTrue(steve.anyMessageContains("needs to be an integer"), steve.getMessages().toString());
    }

    @Test
    void theHelpOfThePluginsCommandIsInEnglish() {
        TestFPlayerSender steve = viewer(Senders.player("Steve"));

        harness.dispatch(awshop, steve, "help");

        assertTrue(steve.anyMessageContains("Move the mouse"), steve.getMessages().toString());
        assertFalse(steve.anyMessageContains("Passe o mouse"), steve.getMessages().toString());
    }

    @Test
    void aReplyAfterAFutureStaysInEnglish() {
        TestFPlayerSender steve = viewer(Senders.player("Steve"));

        harness.dispatch(awshop, steve, "log");
        long until = System.currentTimeMillis() + 5000;
        while (steve.getMessages().isEmpty() && System.currentTimeMillis() < until) {
            Thread.yield();
        }

        assertTrue(steve.anyMessageContains("has no entry for that"), steve.getMessages().toString());
    }

    @Test
    void aReplyCarriedToAnotherThreadKeepsThePluginsLanguage() throws Exception {
        TestFPlayerSender steve = Senders.player("Steve");
        CompletableFuture<String> later = new CompletableFuture<>();
        CompletableFuture<Void> replied;
        try (LocaleScope ignored = LocaleScope.enter(harness.ecPluginData)) {
            replied = later.whenComplete(LocaleScope.carry((value, error) -> FCMessageUtil.NEEDS_TO_BE_INTEGER.addPlaceholder("argumento", value).send(steve)))
                    .thenAccept(value -> { });
        }
        //completed on another thread, after the dispatch's scope was closed
        Thread other = new Thread(() -> later.complete("abc"));
        other.start();
        other.join();
        replied.get(5, TimeUnit.SECONDS);

        assertTrue(steve.anyMessageContains("needs to be an integer"), steve.getMessages().toString());
    }

    @Test
    void theCoresOwnCommandStaysInTheCoresLanguageAndTheScopeDoesNotLeak() {
        FinalCMDPluginCommand coreCount = FinalCMDManager.registerCommand(core, new CoreCount_Cmd()).get(0);
        TestFPlayerSender steve = viewer(Senders.player("Steve"));

        harness.dispatch(awshop, steve, "log --page abc");
        harness.dispatch(coreCount, steve, "abc");
        harness.dispatch(awshop, steve, "log --page xyz");

        assertTrue(steve.anyMessageContains("[§2abc§7]§c needs to be an integer"), steve.getMessages().toString());
        assertTrue(steve.anyMessageContains("por favor use /corecount"), "the core's command, in the core's language: " + steve.getMessages());
        assertTrue(steve.anyMessageContains("[§2xyz§7]§c needs to be an integer"), steve.getMessages().toString());
    }

    @Test
    void thePlayersOwnLanguageWinsTheScope() throws Exception {
        try (Locales locales = Locales.perPlayerLocale(Files.createDirectories(tempDir.resolve("players")))) {
            TestFPlayerSender petrus = viewer(locales.reader("Petrus", LocaleType.PT_BR));

            harness.dispatch(awshop, petrus, "log --kind nonsense");

            assertTrue(petrus.anyMessageContains("Não existe o tipo de auditoria"), petrus.getMessages().toString());
        }
    }

    @Test
    void anotherPluginsMessageKeepsItsLanguageInsideTheScope() {
        ECPluginData shop = Plugins.fakeOther("FakeShop", tempDir.resolve("shop").toFile());
        try {
            Plugins.setLanguage(shop, LocaleType.PT_BR);
            LocaleMessage sold = Locales.message(shop, "FakeShop.SOLD", LocaleType.EN_US, "Sold", LocaleType.PT_BR, "Vendido");
            TestFPlayerSender steve = Senders.player("Steve");

            try (LocaleScope ignored = LocaleScope.enter(harness.ecPluginData)) {
                sold.send(steve);
            }

            assertTrue(steve.anyMessageContains("Vendido"), steve.getMessages().toString());
        } finally {
            Plugins.forget("FakeShop");
        }
    }
}
