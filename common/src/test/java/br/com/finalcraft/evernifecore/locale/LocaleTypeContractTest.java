package br.com.finalcraft.evernifecore.locale;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link LocaleType#normalize(String)}: it uppercases ANY locale name, known or not, using
 * {@link Locale#ROOT} rather than the JVM default; and {@link LocaleType#register(String)} makes a
 * custom locale visible to {@link LocaleType#values()}.
 */
public class LocaleTypeContractTest {

    @Test
    void normalizeUppercasesAnyLocaleName() {
        assertEquals("EN_US", LocaleType.normalize("en_us"));
        assertEquals("ES_ES", LocaleType.normalize("es_es"));
    }

    @Test
    void localeKeysAreStableUnderTurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("TITLE", LocaleType.normalize("title"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void aRegisteredLocaleShowsUpInValues() {
        assertEquals("ES_ES", LocaleType.register("ES_ES"));

        assertTrue(LocaleType.values().contains("ES_ES"),
                "a registered locale must be listed among the known ones: " + LocaleType.values());
        assertTrue(LocaleType.values().contains(LocaleType.EN_US),
                "registering must not displace the built-in locales");
    }

    @Test
    void registeringNormalizesTheNameBeforeStoringIt() {
        assertEquals("FR_FR", LocaleType.register("fr_fr"));

        assertTrue(LocaleType.values().contains("FR_FR"));
    }
}
