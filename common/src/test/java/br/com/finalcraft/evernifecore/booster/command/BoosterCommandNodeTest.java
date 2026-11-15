package br.com.finalcraft.evernifecore.booster.command;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.BoosterJoin;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The booster verbs a duel plugin mounts under its own command: give, list, revoke and explain, over its own domains
 * only - a domain of another plugin is refused with the list of the plugin's.
 */
class BoosterCommandNodeTest {

    private static final String BASE = "fakeduels.boosters";

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private LogicWorld duels;
    private LogicWorld arena;
    private BoosterDomain win;
    private TestFPlayerSender ana;
    private TestCommandSender admin;
    private FinalCMDPluginCommand cmd;

    @FinalCMD(aliases = "duel")
    public static class Duel_Cmd {
        @FinalCMD.Node(subcmd = "boosters", permission = BASE)
        final BoosterCommandNode boosters;

        Duel_Cmd(BoosterCommandNode boosters) {
            this.boosters = boosters;
        }
    }

    @BeforeEach
    void open() {
        harness = Commands.harness("BoosterCmd", tempDir.resolve("cmd"));
        FCLocaleManager.loadLocale(harness.ecPluginData, BoosterCommandNode.class);
        duels = LogicWorld.open("FakeDuels", tempDir.resolve("duels"));
        arena = LogicWorld.open("FakeArena", tempDir.resolve("arena"));
        duels.boosters();
        duels.auditLog();
        win = BoosterDomain.of(duels.getOwner(), "win").join(BoosterJoin.sumBonus()).permission("fakeduels.booster.win", 2).register();
        BoosterDomain.of(arena.getOwner(), "wave").register();
        ana = duels.player("Ana");
        duels.login(ana);
        cmd = harness.register(new Duel_Cmd(new BoosterCommandNode(duels.getOwner(), BASE)));
        admin = Senders.console("admin").grant(BASE).grant(BASE + ".give").grant(BASE + ".list").grant(BASE + ".revoke").grant(BASE + ".explain");
    }

    @AfterEach
    void close() {
        arena.close();
        duels.close();
        harness.close();
    }

    @Test
    void giveGrantsTheBoosterAndAuditsIt() throws Exception {
        harness.dispatch(cmd, admin, "boosters give Ana win 2 1h");

        List<Booster> active = duels.boosters().getActive(ProgressOwner.player(ana.getUniqueId()));
        assertEquals(1, active.size(), active.toString());
        assertEquals("fakeduels:win", active.get(0).getDomainId());
        assertEquals(0, new BigDecimal(2).compareTo(active.get(0).getFactor()));
        waitFor(() -> admin.anyMessageContains("granted"), admin);
        duels.awaitAudit();
        List<AuditEntry> audited = duels.auditLog().query(new AuditQuery().kind(AuditKind.BOOSTER_GRANTED)).get(5, TimeUnit.SECONDS);
        assertEquals(1, audited.size(), audited.toString());
        assertEquals("fakeduels:win", audited.get(0).getText().split(" ")[0]);
    }

    @Test
    void giveReadsABonusInPercent() {
        harness.dispatch(cmd, admin, "boosters give Ana fakeduels:win +50% 30m");

        List<Booster> active = duels.boosters().getActive(ProgressOwner.player(ana.getUniqueId()));
        assertEquals(1, active.size(), active.toString());
        assertEquals(0, new BigDecimal("0.5").compareTo(active.get(0).getBonus()));
    }

    @Test
    void listShowsWhatWasGrantedWithItsId() throws Exception {
        Booster kept = grantAna();

        harness.dispatch(cmd, admin, "boosters list Ana");

        assertTrue(admin.anyMessageContains(kept.getId().toString()), admin.getMessages().toString());
        assertTrue(admin.anyMessageContains("fakeduels:win x2"), admin.getMessages().toString());
    }

    @Test
    void revokeTakesTheBoosterAway() throws Exception {
        Booster kept = grantAna();

        harness.dispatch(cmd, admin, "boosters revoke " + kept.getId());

        assertTrue(duels.boosters().getActive(ProgressOwner.player(ana.getUniqueId())).isEmpty());
        waitFor(() -> admin.anyMessageContains("revoked"), admin);
    }

    @Test
    void explainPrintsTheParcelsAndTheMultiplier() throws Exception {
        ana.grant("fakeduels.booster.win.2");
        duels.boosters().grant(Booster.on(win).bonus(0.5).forOwner(ProgressOwner.player(ana.getUniqueId())).build()).get(5, TimeUnit.SECONDS);

        harness.dispatch(cmd, admin, "boosters explain Ana win");

        String said = String.join("\n", admin.getMessages());
        assertTrue(said.contains("x2.5"), said);
        assertTrue(said.contains("fakeduels:win x2.0 for player:" + ana.getUniqueId()), said);
        assertTrue(said.contains("fakeduels:win +0.5 for player:" + ana.getUniqueId()), said);
    }

    @Test
    void explainLoadsAnOfflinePlayersPermissionsFirst() {
        UUID cris = offline("Cris");
        harness.platform.useTestOfflinePermissions().grant(cris, "fakeduels.booster.win.2");

        harness.dispatch(cmd, admin, "boosters explain Cris win");

        waitFor(() -> admin.anyMessageContains("x2"), admin);
        assertTrue(admin.getMessages().stream().noneMatch(line -> line.contains("not read")), admin.getMessages().toString());
    }

    @Test
    void aDomainOfAnotherPluginIsRefusedListingThisPluginsDomains() {
        harness.dispatch(cmd, admin, "boosters give Ana fakearena:wave 2 1h");

        assertTrue(admin.anyMessageContains("fakeduels:win"), admin.getMessages().toString());
        assertTrue(admin.anyMessageContains("fakearena:wave"), admin.getMessages().toString());
        assertTrue(duels.boosters().getAll().isEmpty());
        assertTrue(arena.boosters().getAll().isEmpty(), "nothing granted on the other plugin either");
    }

    @Test
    void anUnknownPlayerIsRefusedAndNothingIsGranted() {
        harness.dispatch(cmd, admin, "boosters give Ghost win 2 1h");

        assertTrue(admin.anyMessageContains("Ghost"), admin.getMessages().toString());
        assertTrue(duels.boosters().getAll().isEmpty());
    }

    @Test
    void givePlayerCompletesAKnownOfflinePlayer() {
        offline("Cris");

        List<String> offered = harness.tab(cmd, admin, "boosters", "give", "");

        assertTrue(offered.contains("Cris"), offered.toString());
        assertTrue(offered.contains("Ana"), offered.toString());
    }

    @Test
    void theDomainCompletesOnlyThisPluginsDomainsWholeAndShort() {
        List<String> offered = harness.tab(cmd, admin, "boosters", "explain", "Ana", "");

        assertEquals(Arrays.asList("fakeduels:win", "win"), offered);
        assertEquals(Collections.singletonList("win"), harness.tab(cmd, admin, "boosters", "give", "Ana", "w"));
    }

    @Test
    void theShortDomainNameGrants() {
        harness.dispatch(cmd, admin, "boosters give Ana win x3 1h");

        List<Booster> active = duels.boosters().getActive(ProgressOwner.player(ana.getUniqueId()));
        assertEquals(1, active.size(), active.toString());
        assertEquals("fakeduels:win", active.get(0).getDomainId());
    }

    @Test
    void aFactorThatIsNoNumberIsRefusedTeachingBothForms() {
        harness.dispatch(cmd, admin, "boosters give Ana win double 1h");

        String said = String.join("\n", admin.getMessages());
        assertTrue(said.contains("double") && said.contains("+50%"), said);
        assertTrue(duels.boosters().getAll().isEmpty());
    }

    // ------------------------------------------------------------------

    /** A player the server has seen and who is offline now. */
    private UUID offline(String name) {
        TestFPlayerSender player = duels.player(name);
        duels.login(player);
        duels.quit(player);
        return player.getUniqueId();
    }

    private Booster grantAna() throws Exception {
        CompletableFuture<Booster> granted = duels.boosters().grant(Booster.on(win).factor(2).forOwner(ProgressOwner.player(ana.getUniqueId())).build());
        return granted.get(5, TimeUnit.SECONDS);
    }

    /** The verbs that write answer once the storage has the change, on its thread. */
    private static void waitFor(BooleanSupplier said, TestCommandSender sender) {
        long until = System.currentTimeMillis() + 5000;
        while (!said.getAsBoolean()) {
            if (System.currentTimeMillis() > until) {
                throw new AssertionError("never said it: " + sender.getMessages().stream().collect(Collectors.joining(" | ")));
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
