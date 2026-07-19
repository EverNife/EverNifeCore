package br.com.finalcraft.evernifecore.placeholder.replacer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegexReplacerEscapeTest {

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
}
