package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickRuleTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private ExecutionContext ctx() {
        return ExecutionContext.builder(Caller.of(world.getOwner(), "test")).player(alex).build();
    }

    private Condition passing(String name) {
        return ctx -> {
            log.add("check " + name);
            return ConditionResult.pass();
        };
    }

    private Condition failing(String name) {
        return ctx -> {
            log.add("check " + name);
            return ConditionResult.fail(FancyText.of("no " + name));
        };
    }

    private Effect record(String what) {
        return ctx -> log.add(what);
    }

    private Cost cost(String name, boolean affordable, boolean takeWorks) {
        return new Cost() {
            @Override
            public ConditionResult test(ExecutionContext ctx) {
                return affordable ? ConditionResult.pass() : ConditionResult.fail(FancyText.of("cannot pay " + name));
            }

            @Override
            public boolean take(ExecutionContext ctx) {
                log.add("take " + name);
                if (!takeWorks) {
                    throw new IllegalStateException("the bank is down");
                }
                return true;
            }

            @Override
            public void refund(ExecutionContext ctx) {
                log.add("refund " + name);
            }

            @Override
            public FancyText describe(ExecutionContext ctx) {
                return FancyText.of(name);
            }
        };
    }

    @Test
    void aCostThatFailsItsCheckStopsEveryTakeEvenTheOnesBeforeIt() {
        ClickRule rule = ClickRule.on().gate(Gate.of(Arrays.<Condition>asList(), Arrays.asList(
                cost("gold", true, true), cost("gems", false, true)))).onSuccess(record("bought"));

        assertEquals(ClickRule.Outcome.DENIED, rule.run(ctx()));
        assertTrue(log.stream().noneMatch(line -> line.startsWith("take")), "a click never pays half its price: " + log);
    }

    @Test
    void aTakeThatThrowsRefundsTheCostsAlreadyTakenAndDenies() {
        ClickRule rule = ClickRule.on().gate(Gate.of(Arrays.<Condition>asList(), Arrays.asList(
                cost("gold", true, true), cost("gems", true, false)))).onSuccess(record("bought"));

        assertEquals(ClickRule.Outcome.DENIED, rule.run(ctx()));
        assertTrue(log.contains("refund gold"), "what was taken comes back: " + log);
        assertTrue(!log.contains("bought"), "a denied rule runs no success effect");
    }

    @Test
    void whenEverythingPassesEachSuccessEffectRunsOnce() {
        ClickRule rule = ClickRule.on().gate(Gate.builder().require(passing("rank")).cost(cost("gold", true, true)).build())
                .onSuccess(record("first"), record("second"));

        assertEquals(ClickRule.Outcome.SUCCESS, rule.run(ctx()));
        assertEquals(1, log.stream().filter("first"::equals).count());
        assertEquals(1, log.stream().filter("second"::equals).count());
        assertTrue(log.contains("take gold"));
    }

    @Test
    void aFailingConditionDeniesAndNoCostIsTaken() {
        ClickRule rule = ClickRule.on().gate(Gate.builder().require(failing("rank")).cost(cost("gold", true, true)).build());

        assertEquals(ClickRule.Outcome.DENIED, rule.run(ctx()));
        assertTrue(!log.contains("take gold"), "nothing is charged for a click the conditions refuse: " + log);
    }

    @Test
    void theDenialReadsMissingAndTheWholeResult() {
        List<ExecutionContext> denied = new ArrayList<>();
        ClickRule rule = ClickRule.on().gate(Gate.builder().require(failing("rank"), failing("level")).build())
                .onDeny(denied::add);

        rule.run(ctx());

        assertEquals(1, denied.size());
        String missing = denied.get(0).getVariable("missing");
        assertNotNull(missing);
        assertTrue(missing.contains("no rank") && missing.contains("no level"), "every failure, not the first: " + missing);
        GateResult result = denied.get(0).getAttribute(ClickRule.GATE_RESULT, GateResult.class);
        assertNotNull(result);
        assertEquals(2, result.getFailures().size());
    }

    @Test
    void anAtLeastOfNOpensWithNPassesAndDeniesWithOneLess() {
        ClickRule two = ClickRule.on().gate(Gate.builder().require(Conditions.atLeast(2,
                passing("a"), passing("b"), failing("c"))).build());
        ClickRule three = ClickRule.on().gate(Gate.builder().require(Conditions.atLeast(3,
                passing("a"), passing("b"), failing("c"))).build());

        assertEquals(ClickRule.Outcome.SUCCESS, two.run(ctx()));
        assertEquals(ClickRule.Outcome.DENIED, three.run(ctx()));
    }

    @Test
    void anOptionalConditionThatFailsDoesNotDeny() {
        ClickRule rule = ClickRule.on().gate(Gate.builder().require(passing("rank"), Conditions.optional(failing("bonus"))).build())
                .onSuccess(record("opened"));

        assertEquals(ClickRule.Outcome.SUCCESS, rule.run(ctx()));
        assertTrue(log.contains("opened"));
    }

    @Test
    void aPrimaryRuleDoesNotAnswerASecondaryClick() {
        ClickRule rule = ClickRule.on(ClickGesture.PRIMARY);

        assertTrue(rule.respondsTo(ClickGesture.PRIMARY));
        assertTrue(!rule.respondsTo(ClickGesture.SECONDARY));
    }

    @Test
    void aRuleWithoutGesturesAnswersEveryOne() {
        ClickRule rule = ClickRule.on();

        for (ClickGesture gesture : ClickGesture.values()) {
            assertTrue(rule.respondsTo(gesture), gesture.name());
        }
    }

    @Test
    void dispatchRunsTheFirstRuleThatAnswersTheClickAndOnlyIt() {
        Screen<?> screen = Screen.of(1).debounce(0);
        screen.icon(0, Icon.of(ItemSpec.of("minecraft:stone", 1))).id("buy").rule(
                ClickRule.on(ClickGesture.PRIMARY).onSuccess(record("primary")),
                ClickRule.on().onSuccess(record("fallback")),
                ClickRule.on(ClickGesture.SECONDARY).onSuccess(record("never")));
        ScreenView view = world.open(screen, alex);

        world.click(view, "buy", ClickGesture.SECONDARY);
        world.click(view, "buy", ClickGesture.PRIMARY);

        assertEquals(Arrays.asList("fallback", "primary"), log, "a rule after the one that answered never runs");
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void aDeniedRuleRunsItsDenyEffectsAndNotItsSuccessOnes() {
        ClickRule rule = ClickRule.on().gate(Gate.builder().require(failing("rank")).build())
                .onSuccess(record("success")).onDeny(record("deny"));

        rule.run(ctx());

        assertTrue(log.contains("deny") && !log.contains("success"), String.valueOf(log));
    }

    @Test
    void theCostPreviewChargesNothing() {
        ClickRule rule = ClickRule.on().gate(Gate.builder().cost(cost("gold", true, true)).build());

        String described = rule.describeCost(ctx());

        assertTrue(described.contains("gold"), described);
        assertTrue(log.stream().noneMatch(line -> line.startsWith("take")), "a preview takes nothing: " + log);
    }

}
