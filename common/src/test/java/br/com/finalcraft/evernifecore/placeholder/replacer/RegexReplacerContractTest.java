package br.com.finalcraft.evernifecore.placeholder.replacer;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.manipulation.Manipulator;
import br.com.finalcraft.evernifecore.placeholder.FCRegexReplacers;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Anti-regression pins for {@link RegexReplacer}'s current, already-correct contract: the closure
 * syntax, the fluent builder, and the escaping of the replacement value ('\' and '$').
 */
public class RegexReplacerContractTest {

    @Test
    void aTokenWithNoRegisteredParserAndNoDefaultParserIsLeftRaw() {
        RegexReplacer<Object> replacer = new RegexReplacer<>().addParser("known", o -> "value");

        assertEquals("keep %unknown% as-is", replacer.apply("keep %unknown% as-is", new Object()));
    }

    @Test
    void setDefaultParserIsUsedOnlyWhenNoNamedParserMatched() {
        RegexReplacer<Object> replacer = new RegexReplacer<Object>()
                .addParser("known", o -> "named")
                .setDefaultParser((o, name) -> "default:" + name);

        assertEquals("named", replacer.apply("%known%", new Object()));
        assertEquals("default:whatever", replacer.apply("%whatever%", new Object()));
    }

    @Test
    void applyOnAListReturnsANewListAndLeavesTheOriginalUntouched() {
        RegexReplacer<Object> replacer = new RegexReplacer<>().addParser("name", o -> "Steve");
        List<String> lines = Arrays.asList("Hello %name%", "Bye %name%");

        List<String> result = replacer.apply(lines, new Object());

        assertNotSame(lines, result, "apply(List, O) must return a NEW list, never the one it was given");
        assertEquals(Arrays.asList("Hello Steve", "Bye Steve"), result);
        assertEquals(Arrays.asList("Hello %name%", "Bye %name%"), lines,
                "the original list must be left untouched");
    }

    // The regex engine is already match-driven: a parser that is registered but never cited in the
    // text is never invoked. This is the behaviour placeholder(Supplier) has to equal on the closure
    // engine once it exists.
    @Test
    void unreferencedParserIsNeverInvoked() {
        AtomicInteger calls = new AtomicInteger();
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addParser("expensive", o -> { calls.incrementAndGet(); return "x"; });

        assertEquals("nothing here", replacer.apply("nothing here", new Object()));
        assertEquals(0, calls.get(), "an unreferenced parser must not be resolved");
    }

    // ------------------------------------------------------------------
    //  absorbed from RegexReplacerClosureContractTest
    // ------------------------------------------------------------------

    // quoteAndParse must quote with the closure the wrapped RegexReplacer was built with, so a
    // replacer built on '{}' is reachable through it.
    @Test
    void quoteAndParseUsesTheReplacersOwnClosureNotAlwaysPercent() {
        RegexReplacer<Object> bracketReplacer = new RegexReplacer<>(Closures.BRACKET.getPattern())
                .addParser("foo", o -> "bar");

        // the replacer itself resolves 'foo' fine when quoted with its OWN closure...
        assertEquals("bar", bracketReplacer.apply("{foo}", new Object()));

        Manipulator manipulator = new Manipulator("prefix_{something}");
        ManipulationContext.RContext<Object> context =
                new ManipulationContext.RContext<>(manipulator, "prefix_{something}", bracketReplacer);

        // ...but quoteAndParse always wraps with '%...%', so it can never reach a '{}' replacer.
        assertNotNull(context.quoteAndParse(new Object(), "foo"),
                "quoteAndParse must quote using the replacer's own Closures, not hardcode PERCENT");
    }

    // A literal '%' that is not part of a placeholder (e.g. "100%") must not be paired by the regex
    // with the NEXT real placeholder's opening '%', which would consume one of its delimiters and
    // leave it unrecognised.
    @Test
    void percentLiteralBeforeAPlaceholderDoesNotConsumeItsDelimiter() {
        RegexReplacer<Object> replacer = new RegexReplacer<>().addParser("player", o -> "Steve");

        assertEquals("100% of Steve", replacer.apply("100% of %player%", new Object()));
    }

    // ------------------------------------------------------------------
    //  absorbed from RegexReplacerEscapeTest
    // ------------------------------------------------------------------

    // A replacement value with backslashes (a Windows path) or ending in a lone backslash must be
    // inserted literally. The former escape only handled '$'; a trailing '\' would make
    // appendReplacement throw. Matcher.quoteReplacement escapes both '\' and '$'.
    @Test
    public void backslashAndDollarValuesArePreservedLiterally() {
        RegexReplacer<Object> replacer = new RegexReplacer<>()
                .addParser("path", o -> "C:\\Users\\x")
                .addParser("dir", o -> "C:\\Users\\")
                .addParser("price", o -> "$5 off");

        assertEquals("C:\\Users\\x", replacer.apply("%path%", new Object()));
        assertDoesNotThrow(() -> replacer.apply("%dir%", new Object()));
        assertEquals("C:\\Users\\", replacer.apply("%dir%", new Object()));
        assertEquals("$5 off", replacer.apply("%price%", new Object()));
    }

    // ------------------------------------------------------------------
    //  absorbed from RegexReplacerFluentApiTest
    // ------------------------------------------------------------------

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
