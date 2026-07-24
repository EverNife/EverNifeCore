package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.manipulation.Manipulator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins that a {@link RegexReplacer} is treated as owning a single, consistent closure.
 */
public class RegexReplacerClosureContractTest {

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
}
