package br.com.finalcraft.evernifecore.quest;

import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parameter values of one quest instance, frozen at its start: {@code {type}} in the quest's texts and objective
 * filters reads them. Immutable.
 */
public final class QuestParams {

    public static final QuestParams NONE = new QuestParams(Collections.<String, String>emptyMap());

    private static final Pattern PARAM = Pattern.compile("\\{([a-z0-9_-]+)}");

    private final Map<String, String> values;

    private QuestParams(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** {@code of("type", "fire", "amount", "5")}: name, value, name, value... */
    public static QuestParams of(String... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("QuestParams.of takes name, value pairs - got an odd count: " + namesAndValues.length + ".");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            values.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return new QuestParams(values);
    }

    public static QuestParams of(Map<String, String> values) {
        return values.isEmpty() ? NONE : new QuestParams(values);
    }

    public @Nullable String get(String name) {
        return values.get(name);
    }

    public Map<String, String> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** {@code text} with every {@code {name}} of these params replaced; other keys stay as written. */
    public String apply(String text) {
        if (values.isEmpty() || text.indexOf('{') < 0) {
            return text;
        }
        Matcher matcher = PARAM.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuestParams && values.equals(((QuestParams) other).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
