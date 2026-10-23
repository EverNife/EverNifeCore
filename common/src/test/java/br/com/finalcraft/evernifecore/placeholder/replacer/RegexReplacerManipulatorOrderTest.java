package br.com.finalcraft.evernifecore.placeholder.replacer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two manipulators that tie on the ordering rule (same prefix, same number of {@code _}) keep their
 * registration order: the first registered answers first, however many are registered after it.
 */
class RegexReplacerManipulatorOrderTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 7})
    void theFirstRegisteredOfTwoTiedManipulatorsWinsWhateverComesAfter(int registeredAfter) {
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addManipulator("job_{name}_level", (o, ctx) -> "first")
                .addManipulator("job_{id}_level", (o, ctx) -> "second");
        for (int i = 0; i < registeredAfter; i++) {
            replacer.addManipulator("other" + i + "_{value}", (o, ctx) -> "other");
        }

        assertEquals("first", replacer.apply("%job_miner_level%", new Object()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void bothOverloadsOrderByTheSameRule(int registeredAfter) {
        RegexReplacer<Object> inner = new RegexReplacer<>();
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addManipulator("job_{name}_level", (o, ctx) -> "first")
                .addManipulator("job_{id}_level", inner, (o, ctx) -> "second");
        for (int i = 0; i < registeredAfter; i++) {
            replacer.addManipulator("other" + i + "_{value}", inner, (o, ctx) -> "other");
        }

        assertEquals("first", replacer.apply("%job_miner_level%", new Object()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aLongerPrefixStillAnswersBeforeAShorterOne(int registeredAfter) {
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addManipulator("job_{name}", (o, ctx) -> "short")
                .addManipulator("job_miner_{name}", (o, ctx) -> "long");
        for (int i = 0; i < registeredAfter; i++) {
            replacer.addManipulator("other" + i + "_{value}", (o, ctx) -> "other");
        }

        assertEquals("long", replacer.apply("%job_miner_x%", new Object()));
    }
}
