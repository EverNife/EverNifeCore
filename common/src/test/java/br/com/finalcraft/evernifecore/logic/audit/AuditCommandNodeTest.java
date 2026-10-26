package br.com.finalcraft.evernifecore.logic.audit;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.audit.command.AuditCommandNode;
import br.com.finalcraft.evernifecore.pageviewer.nav.PVExtraMessages;
import br.com.finalcraft.evernifecore.pageviewer.theme.ClassicPageTheme;
import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The audit view a plugin mounts on its own command: it pages the log and refuses without {@code <base>.view}. */
class AuditCommandNodeTest {

    private static final String BASE = "awshop.command.awshop.log";

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private ECStorage storage;
    private AuditLog log;
    private TestFPlayerSender steve;

    @BeforeEach
    void open() throws Exception {
        harness = Commands.harness("AuditCmd", tempDir.resolve("cmd"));
        FCLocaleManager.loadLocale(harness.ecPluginData, ClassicPageTheme.class, PVExtraMessages.class, AuditCommandNode.class);
        ScriptedStorage backend = ScriptedStorage.inMemory();
        backend.init().get(5, TimeUnit.SECONDS);
        storage = backend.asECStorage();
        log = AuditLog.of(harness.ecPluginData, storage);
        steve = Senders.player("Steve");
        for (int i = 0; i < 25; i++) {
            log.record(AuditEntry.builder(AuditKind.TRANSFER, Caller.of(harness.ecPluginData, "awshop:give"))
                    .at(Instant.ofEpochSecond(1_000 + i))
                    .player(steve.getUniqueId())
                    .subject("evernifecore:money")
                    .amount(new BigDecimal(i))
                    .build());
        }
        log.awaitWrites().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void close() throws Exception {
        AuditLog.closeOwnedBy(harness.ecPluginData);
        storage.close().get(5, TimeUnit.SECONDS);
        harness.close();
    }

    /** {@code /awshop log ...} over {@code node}, as a shop plugin would mount it. */
    @FinalCMD(aliases = "awshop")
    public static class AwShop_Cmd {
        @FinalCMD.Node(subcmd = "log", permission = BASE)
        final AuditCommandNode log;

        AwShop_Cmd(AuditCommandNode log) {
            this.log = log;
        }
    }

    private FinalCMDPluginCommand mount() {
        return harness.register(new AwShop_Cmd(new AuditCommandNode(log, BASE)));
    }

    private List<String> transferLines() {
        return steve.getMessages().stream().filter(line -> line.contains("transfer")).collect(Collectors.toList());
    }

    private void awaitLines(int atLeast) {
        long until = System.currentTimeMillis() + 5000;
        while (transferLines().size() < atLeast && System.currentTimeMillis() < until) {
            Thread.yield();
        }
    }

    @Test
    void theViewPagesTheLogTwentyLinesAtATimeNewestFirst() {
        steve.grant(BASE).grant(BASE + ".view");
        FinalCMDPluginCommand awshop = mount();

        harness.dispatch(awshop, steve, "log");
        awaitLines(20);

        List<String> page = transferLines();
        assertEquals(AuditCommandNode.PAGE_SIZE, page.size(), steve.getMessages().toString());
        assertTrue(page.get(0).contains(" 24 "), "newest first: " + page.get(0));
        assertTrue(page.get(0).contains(steve.getUniqueId().toString()) && page.get(0).contains("awshop:give"), page.get(0));

        steve.clearMessages();
        harness.dispatch(awshop, steve, "log --page 2");
        awaitLines(5);
        assertEquals(5, transferLines().size(), steve.getMessages().toString());
    }

    @Test
    void withoutTheViewPermissionNothingIsRead() {
        steve.grant(BASE);
        FinalCMDPluginCommand awshop = mount();

        harness.dispatch(awshop, steve, "log --kind transfer");

        assertTrue(transferLines().isEmpty(), steve.getMessages().toString());
        assertFalse(steve.getMessages().isEmpty(), "the refusal is said");
    }
}
