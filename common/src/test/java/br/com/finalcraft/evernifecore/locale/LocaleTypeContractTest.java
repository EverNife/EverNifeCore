package br.com.finalcraft.evernifecore.locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the confirmed bugs in {@link LocaleType#normalize(String)}: it only recognises the 3
 * hardcoded locale constants (via a reflection map built once at class-load) and returns anything
 * else UNCHANGED instead of uppercased, and even the recognised path uses the JVM's default locale
 * for {@code toUpperCase} instead of {@link Locale#ROOT}.
 */
public class LocaleTypeContractTest {

    @Test
    @Tag("known-bug")
    void normalizeUppercasesAnyLocaleName() {
        assertEquals("EN_US", LocaleType.normalize("en_us"));
        assertEquals("ES_ES", LocaleType.normalize("es_es"));
    }

    @Test
    @Tag("known-bug")
    void localeKeysAreStableUnderTurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("TITLE", LocaleType.normalize("title"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
