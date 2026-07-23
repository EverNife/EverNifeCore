package br.com.finalcraft.evernifecore.placeholder.replacer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Anti-regression pins for {@link RegexReplacer}'s current, already-correct contract. Escaping of
 * the replacement value ('\' and '$') is already pinned by {@code RegexReplacerEscapeTest} in this
 * package - not duplicated here.
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
    void applyOnAListMutatesEachElementInPlaceAndReturnsTheSameList() {
        RegexReplacer<Object> replacer = new RegexReplacer<>().addParser("name", o -> "Steve");
        List<String> lines = Arrays.asList("Hello %name%", "Bye %name%");

        List<String> result = replacer.apply(lines, new Object());

        assertSame(lines, result, "apply(List, O) returns the very same list instance it was given");
        assertEquals(Arrays.asList("Hello Steve", "Bye Steve"), lines);
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
}
