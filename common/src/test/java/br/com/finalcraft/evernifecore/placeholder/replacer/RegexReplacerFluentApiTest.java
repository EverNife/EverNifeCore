package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.FCRegexReplacers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two things a plugin integrating with the placeholder engine depends on: every {@code addParser}
 * overload keeps the chain going (the constant-value one used to widen the return type to the base
 * interface, which ended the chain right there), and {@code describeAll} exposes the registered keys
 * in the order they were declared, which is the order a generated listing shows them in.
 */
class RegexReplacerFluentApiTest {

    @Test
    void everyAddParserOverloadKeepsTheChainOnTheReplacer() {
        // The point of this test is that it COMPILES: each call has to hand back a RegexReplacer, or
        // the next link (here the manipulator, which only RegexReplacer declares) would not resolve.
        RegexReplacer<String> replacer = new RegexReplacer<String>(Closures.PERCENT)
                .addParser("constant", "a constant")
                .addParser("described_constant", "a described constant", "another constant")
                .addParser("computed", subject -> subject.toUpperCase())
                .addParser("described_computed", "a described function", subject -> subject.length())
                .addManipulator("upper_{word}", (subject, context) -> context.getString("{word}").toUpperCase());

        assertEquals("a constant / another constant / OWNER / 5 / TAIL",
                replacer.apply("%constant% / %described_constant% / %computed% / %described_computed% / %upper_tail%", "owner"));
    }

    @Test
    void describeAllExposesEveryKeyWithItsDescriptionInRegistrationOrder() {
        Map<String, String> described = new RegexReplacer<String>(Closures.PERCENT)
                .addParser("first", "the first one", "x")
                .addParser("second", subject -> subject)
                .addParser("third", "the third one", subject -> subject)
                .describeAll();

        assertEquals(Arrays.asList("first", "second", "third"), new ArrayList<>(described.keySet()),
                "the listing order must be the registration order");
        assertEquals("the first one", described.get("first"));
        assertEquals("", described.get("second"), "an undescribed key still shows up, with an empty description");
        assertEquals("the third one", described.get("third"));
    }

    @Test
    void describeAllOfTheCorePlayerDataReplacerListsEveryRegisteredKey() {
        List<String> expected = Arrays.asList(
                "player",
                "player_name",
                "player_uuid",
                "player_is_online",
                "player_ontime",
                "player_last_seen",
                "player_last_seen_millis",
                "player_first_seen",
                "player_first_seen_millis");

        Map<String, String> described = FCRegexReplacers.PLAYER_DATA.describeAll();

        assertEquals(expected, new ArrayList<>(described.keySet()),
                "every key the core registers must be listed, in the order it was registered");
    }
}
