package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * One level of {@code ${key}} visibility during a single render: the values declared right here,
 * the outer level they fall back to, and the values already computed - so a key cited twice in the
 * same render costs exactly one call.
 */
final class PlaceholderScope {

    private final PlaceholderScope parent;
    private final Map<String, PlaceholderValue> values;
    private final Map<String, String> alreadyResolved = new HashMap<>();

    PlaceholderScope(@Nullable PlaceholderScope parent, Map<String, PlaceholderValue> values) {
        this.parent = parent;
        this.values = values;
    }

    /** Keys are matched case-insensitively, so {@code ${saldo}} and {@code ${SALDO}} are one key. */
    static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    /** @return the text to substitute, or {@code null} when no level of the chain answers for the key. */
    private @Nullable String valueOf(String key, RenderContext context) {
        if (!values.containsKey(key)) {
            return parent == null ? null : parent.valueOf(key, context);
        }
        // A HashMap and containsKey rather than computeIfAbsent: null is a legitimate answer that
        // must be remembered, otherwise the value would be recomputed on every mention.
        if (alreadyResolved.containsKey(key)) {
            return alreadyResolved.get(key);
        }
        Object raw = values.get(key).resolve(context);
        String value = raw == null ? null : String.valueOf(raw);
        alreadyResolved.put(key, value);
        return value;
    }

    /** Replaces every {@code ${key}} this scope chain answers for; the rest is left as written. */
    @Nullable String render(@Nullable String text, RenderContext context) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Closures.DOLLAR_CURLY.getPattern().matcher(text);
        if (!matcher.find()) {
            return text;   // no closure in the text at all: nothing is ever computed
        }

        StringBuilder out = new StringBuilder();
        int copiedUpTo = 0;
        do {
            String value = valueOf(normalizeKey(matcher.group("key")), context);
            if (value != null) {
                out.append(text, copiedUpTo, matcher.start()).append(value);
                copiedUpTo = matcher.end();
            }
        }
        while (matcher.find());

        return out.append(text, copiedUpTo, text.length()).toString();
    }
}
