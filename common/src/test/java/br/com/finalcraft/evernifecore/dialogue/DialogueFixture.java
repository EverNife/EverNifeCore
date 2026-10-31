package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Cost;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

/** A screen world, a logic owner ({@code FakeColonies}) and a viewer, torn down in the order a dialogue needs. */
final class DialogueFixture implements AutoCloseable {

    final ScreenWorld screens;
    final LogicWorld colonies;
    final TestFPlayerSender alex;

    private DialogueFixture(Path dir, UnaryOperator<Platforms> platform) {
        screens = ScreenWorld.open(dir.resolve("screens"), platform);
        alex = screens.viewer("Alex");
        colonies = LogicWorld.open("FakeColonies", dir.resolve("colonies"));
    }

    static DialogueFixture open(Path dir) {
        return new DialogueFixture(dir, UnaryOperator.identity());
    }

    static DialogueFixture open(Path dir, UnaryOperator<Platforms> platform) {
        return new DialogueFixture(dir, platform);
    }

    ECPluginData owner() {
        return colonies.getOwner();
    }

    /** Registers {@code dialogue} under its owner and hands it back. */
    Dialogue register(Dialogue dialogue) {
        Dialogues.of(dialogue.getOwner()).register(dialogue);
        return dialogue;
    }

    DialogueSession start(Dialogue dialogue) {
        return Dialogues.start(alex, dialogue, colonies.caller("test"), null);
    }

    ScreenView view() {
        return Screens.getOpenView(alex);
    }

    RecordingSurface surface() {
        return screens.getRenderer().surfaceOf(view());
    }

    @Override
    public void close() {
        Dialogues.closeOwnedBy(owner());
        screens.advanceTicks(2);
        ScreenView left = view(); //a terminal node's last line, or a screen whose player quit, stays until closed
        if (left != null) {
            left.close();
            screens.advanceTicks(2);
        }
        colonies.close();
        screens.close();
    }

    /** A condition the test flips; it fails with {@code why} and is asked again when one of {@code needs} fires. */
    static final class Toggle implements Condition {

        volatile boolean passes;
        private final String why;
        private final Set<EventSource> needs;

        Toggle(boolean passes, String why, EventSource... needs) {
            this.passes = passes;
            this.why = why;
            this.needs = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(needs)));
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return ConditionResult.of(passes, FancyText.of(why));
        }

        @Override
        public Set<EventSource> getNeeds() {
            return needs;
        }

        @Override
        public String toString() {
            return "toggle(" + why + ")";
        }
    }

    /** A purse of coins: the cost a gate takes and refunds. */
    static final class Purse implements Cost {

        int coins;
        int takes;
        private final int price;

        Purse(int coins, int price) {
            this.coins = coins;
            this.price = price;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return ConditionResult.of(coins >= price, FancyText.of("Missing " + (price - coins) + " coins"));
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            if (coins < price) {
                return false;
            }
            coins -= price;
            takes++;
            return true;
        }

        @Override
        public void refund(ExecutionContext ctx) {
            coins += price;
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return FancyText.of(price + " coins");
        }

        @Override
        public String toString() {
            return "purse(" + price + ")";
        }
    }
}
