package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.LogicFixtures.JournalCost;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.CostRefundEvent;
import br.com.finalcraft.evernifecore.logic.audit.CostTakeEvent;
import br.com.finalcraft.evernifecore.logic.audit.GateCheckEvent;
import br.com.finalcraft.evernifecore.logic.audit.GateDeniedEvent;
import br.com.finalcraft.evernifecore.logic.audit.GatePaidEvent;
import br.com.finalcraft.evernifecore.logic.audit.GatePrePayEvent;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static br.com.finalcraft.evernifecore.logic.LogicFixtures.fixed;
import static br.com.finalcraft.evernifecore.logic.LogicFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checking every condition, paying in order, refunding in reverse, the compositions and the gate events. */
class GateTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private final List<String> journal = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().playerState().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
    }

    @AfterEach
    void tearDown() {
        shop.close();
        world.close();
    }

    private ExecutionContext ctx() {
        return shop.context(alex).build();
    }

    @Test
    void payTakesCostsInOrderAndRefundsInReverseWhenTheThirdTakeFails() {
        JournalCost coins = new JournalCost("coins", journal);
        JournalCost gems = new JournalCost("gems", journal);
        JournalCost token = new JournalCost("token", journal);
        token.takes = false;
        ExecutionContext ctx = ctx();

        GateResult result = Gate.builder().cost(coins, gems, token).build().pay(ctx);

        assertEquals(Arrays.asList("take coins", "take gems", "take token", "refund gems", "refund coins"), journal);
        assertEquals(GateResult.Outcome.REFUNDED, result.getOutcome());
        assertFalse(result.isPassed());
        assertTrue(result.getFailures().get(0).getWhy().toPlainText().contains("1 token could not be taken"), result.toString());
        assertEquals(2, ctx.getAudit().getEntries(AuditKind.COST_REFUNDED).size());
    }

    @Test
    void theFirstCostThatDoesNotComeOutDeniesWithNothingToRefund() {
        JournalCost coins = new JournalCost("coins", journal);
        coins.takes = false;

        GateResult result = Gate.builder().cost(coins).build().pay(ctx());

        assertEquals(GateResult.Outcome.DENIED, result.getOutcome());
        assertEquals(Arrays.asList("take coins"), journal);
    }

    @Test
    void aRefundThatThrowsIsAuditedAsRefundFailedWithTheCostDescriptionAndLoggedSevere() {
        JournalCost coins = new JournalCost("coins", journal);
        coins.refundThrows = true;
        JournalCost token = new JournalCost("token", journal);
        token.takes = false;
        ExecutionContext ctx = ctx();

        List<String> logged = Logs.capture(() -> Gate.builder().cost(coins, token).build().pay(ctx));

        List<AuditEntry> failed = ctx.getAudit().getEntries(AuditKind.REFUND_FAILED);
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).getText().contains("1 coins") && failed.get(0).getText().contains("give it back by hand"), failed.get(0).getText());
        assertTrue(failed.get(0).getCause() instanceof IllegalStateException);
        assertTrue(logged.stream().anyMatch(line -> line.contains("[SEVERE]") && line.contains("1 coins") && line.contains("give it back by hand")),
                String.valueOf(logged));
    }

    @Test
    void checkListsEveryFailureInDeclarationOrder() {
        JournalCost coins = new JournalCost("coins", journal);
        coins.affordable = false;
        Gate gate = Gate.builder().require(fixed("vip", false), fixed("level", true), fixed("quest", false)).cost(coins).build();

        GateResult result = gate.check(ctx());

        assertEquals(GateResult.Outcome.DENIED, result.getOutcome());
        List<String> whys = new ArrayList<>();
        for (ConditionResult failure : result.getFailures()) {
            whys.add(failure.getWhy().toPlainText());
        }
        assertEquals(Arrays.asList("vip is not met", "quest is not met", "cannot afford coins"), whys);
        assertTrue(journal.isEmpty(), "a check never takes");
        assertEquals("vip is not met\nquest is not met\ncannot afford coins", result.explain().toPlainText());
    }

    @Test
    void atLeastTwoPassesWithTwoAndDeniesWithOneListingTheFailures() {
        Condition two = Conditions.atLeast(2, fixed("a", true), fixed("b", true), fixed("c", false));
        Condition one = Conditions.atLeast(2, fixed("a", true), fixed("b", false), fixed("c", false));

        assertTrue(Gate.builder().require(two).build().check(ctx()).isPassed());
        GateResult denied = Gate.builder().require(one).build().check(ctx());
        assertFalse(denied.isPassed());
        assertEquals(2, denied.getFailures().size());
        assertEquals("b is not met", denied.getFailures().get(0).getWhy().toPlainText());
        assertEquals("c is not met", denied.getFailures().get(1).getWhy().toPlainText());
    }

    @Test
    void aFailedOptionalDoesNotDenyAndShowsInTheNotes() {
        GateResult result = Gate.builder().require(fixed("vip", true), Conditions.optional(fixed("playtime", false))).build().check(ctx());

        assertTrue(result.isPassed());
        assertEquals(1, result.getNotes().size());
        assertEquals("playtime is not met", result.getNotes().get(0).getWhy().toPlainText());
    }

    @Test
    void anyFailsWithEveryFailureWhenNoneHoldsAndNoneNamesWhatHeld() {
        ConditionResult any = Conditions.test(Conditions.any(fixed("a", false), fixed("b", false)), ctx());
        assertFalse(any.isPassed());
        assertEquals(2, any.getFailures().size());
        assertTrue(Conditions.test(Conditions.any(fixed("a", false), fixed("b", true)), ctx()).isPassed());

        ConditionResult none = Conditions.test(Conditions.none(fixed("banned", true), fixed("muted", false)), ctx());
        assertFalse(none.isPassed());
        assertTrue(none.getWhy().toPlainText().contains("banned"), none.toString());
        assertTrue(Conditions.test(Conditions.none(fixed("banned", false)), ctx()).isPassed());
    }

    @Test
    void getNeedsOfAllIsTheUnionOfItsChildren() {
        Condition all = Conditions.all(fixed("a", true, EventSource.MOVE), fixed("b", true, EventSource.BREAK, EventSource.MOVE),
                Conditions.optional(fixed("c", true, EventSource.PLACE)));

        assertEquals(new LinkedHashSet<>(Arrays.asList(EventSource.MOVE, EventSource.BREAK, EventSource.PLACE)), all.getNeeds());
        assertEquals(all.getNeeds(), Gate.builder().require(all).build().getNeeds());
    }

    @Test
    void aConditionThatThrowsFailsWithoutBreakingTheGateAndIsAudited() {
        Condition broken = ctx -> {
            throw new IllegalStateException("no database");
        };
        ExecutionContext ctx = ctx();

        GateResult result = Gate.builder().require(broken, fixed("vip", false)).build().check(ctx);

        assertEquals(2, result.getFailures().size());
        assertTrue(result.getFailures().get(0).getWhy().toPlainText().startsWith("Could not test"), result.toString());
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.CONDITION_THREW).size());
    }

    @Test
    void aCancelledGatePrePayEventDeniesWithoutAnyTake() {
        JournalCost coins = new JournalCost("coins", journal);
        ECEventSubscription<GatePrePayEvent> veto = ECEventBus.global().subscribe(GatePrePayEvent.class, event -> event.setCancelled(true));
        try {
            List<GateDeniedEvent> denied = shop.recorded(GateDeniedEvent.class);

            GateResult result = Gate.builder().cost(coins).build().pay(ctx());

            assertEquals(GateResult.Outcome.DENIED, result.getOutcome());
            assertTrue(journal.isEmpty(), "nothing is taken: " + journal);
            assertTrue(result.explain().toPlainText().contains("cancelled"), result.toString());
            assertEquals(1, denied.size());
        } finally {
            veto.unsubscribe();
        }
    }

    @Test
    void aPaidGatePostsCheckTakeAndPaidEventsAndAuditsThePayment() {
        List<GateCheckEvent> checks = shop.recorded(GateCheckEvent.class);
        List<CostTakeEvent> takes = shop.recorded(CostTakeEvent.class);
        List<GatePaidEvent> paid = shop.recorded(GatePaidEvent.class);
        List<CostRefundEvent> refunds = shop.recorded(CostRefundEvent.class);
        ExecutionContext ctx = ctx();

        GateResult result = Gate.builder().require(fixed("vip", true)).cost(new JournalCost("coins", journal), new JournalCost("gems", journal))
                .build().pay(ctx);

        assertEquals(GateResult.Outcome.PAID, result.getOutcome());
        assertEquals(1, checks.size());
        assertEquals(2, takes.size());
        assertTrue(takes.get(0).isTaken());
        assertEquals(1, paid.size());
        assertEquals("fakeshop:test", paid.get(0).getCaller().toString());
        assertTrue(refunds.isEmpty());
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.GATE_PAID).size());
        assertEquals("1 coins, 1 gems", result.describeCosts());
    }

    @Test
    void aGateReadFromYamlKeepsAnAbsentConditionPendingUntilItsTypeRegisters() {
        Gate gate = Gate.parse(shop.getOwner(), node("{'require': [{'type': 'all', 'conditions': [{'type': 'fakeshop:vip', 'rank': 'gold'}]}]}"),
                ZoneOffset.UTC);

        GateResult pending = gate.check(ctx());
        assertFalse(pending.isPassed());
        assertTrue(pending.explain().toPlainText().contains("No condition 'fakeshop:vip' is registered"), pending.toString());

        Conditions.registry().register(shop.getOwner(), TypeDescriptor.<Condition>of("fakeshop:vip")
                .describe("Whether the player has a rank.")
                .param(ParamSpec.required("rank", ParamSpec.Kind.STRING, "The rank."))
                .factory(params -> ctx -> ConditionResult.of("gold".equals(params.getString("rank")), FancyText.of("not gold")))
                .build());

        assertTrue(gate.check(ctx()).isPassed());
    }
}
