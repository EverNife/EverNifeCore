package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.manipulation.Manipulator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proves the confirmed bugs where a {@link RegexReplacer} is not treated as owning a single,
 * consistent closure.
 */
public class RegexReplacerClosureContractTest {

    // ManipulationContext.RContext.quoteAndParse hardcodes Closures.PERCENT regardless of which
    // Closures the wrapped RegexReplacer was actually built with, so a replacer built on '{}' can
    // never be reached through it.
    @Test
    @Tag("known-bug")
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

    // A literal '%' that is not part of a placeholder (e.g. "100%") can be paired by the regex with
    // the NEXT real placeholder's opening '%', consuming one of its delimiters so it is never
    // recognised at all.
    @Test
    @Tag("known-bug")
    void percentLiteralBeforeAPlaceholderDoesNotConsumeItsDelimiter() {
        RegexReplacer<Object> replacer = new RegexReplacer<>().addParser("player", o -> "Steve");

        assertEquals("100% of Steve", replacer.apply("100% of %player%", new Object()));
    }
}
