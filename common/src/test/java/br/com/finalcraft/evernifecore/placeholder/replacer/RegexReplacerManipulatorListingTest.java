package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.manipulation.Manipulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a listing command reads to show the manipulators a replacer answers for. */
class RegexReplacerManipulatorListingTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(nullValues = "null", value = {
            "battle_win_[filters?], battle_win",
            "spec_[a?]_[b?], spec",
            "pokemon_[filters?]_count, pokemon_count",
            "top_{number}_battle_win, null",
            "spec_contains_[filters], null",
            "party_slot_{slot}_[filters?], null",
            "{kit}_{operation}, null",
    })
    void theBareFormLeavesEveryOptionalBlockOut(String template, String bareForm) {
        assertEquals(bareForm, new Manipulator(template).bareForm());
    }

    @Test
    void manipulatorsAreDescribedInRegistrationOrderWhateverTheMatchingOrder() {
        RegexReplacer<Object> inner = new RegexReplacer<>();
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addManipulator("job_{name}", "a job's level", (o, ctx) -> "short")
                .addManipulator("job_miner_{name}", (o, ctx) -> "long")
                .addManipulator("battle_win_[filters?]", "battles won, filtered", inner, (o, ctx) -> "won");

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("job_{name}", "a job's level");
        expected.put("job_miner_{name}", "");
        expected.put("battle_win_[filters?]", "battles won, filtered");

        assertEquals(expected, replacer.describeManipulators());
        assertEquals(Arrays.asList("job_{name}", "job_miner_{name}", "battle_win_[filters?]"),
                new ArrayList<>(replacer.describeManipulators().keySet()));
        assertEquals("long", replacer.apply("%job_miner_x%", new Object()));   //matching still prefers the longer prefix
    }

    @Test
    void aDescribedManipulatorStillAnswers() {
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addManipulator("battle_win_[filters?]", "battles won", (o, ctx) -> ctx.getArgs("[filters]").isEmpty() ? "all" : "some");

        assertEquals("all", replacer.apply("%battle_win%", new Object()));
        assertEquals("some", replacer.apply("%battle_win_[x]%", new Object()));
        assertEquals("battles won", replacer.getManipulators().get(0).getDescription());
    }
}
