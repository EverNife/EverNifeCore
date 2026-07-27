package br.com.finalcraft.evernifecore.placeholder.base;

import br.com.finalcraft.evernifecore.placeholder.parser.SimpleParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PlaceholderProvider<O> implements IProvider<O>{

    // Keys are indexed lower-cased so a placeholder resolves regardless of how it was typed in the
    // text; SimpleParser keeps the name as registered, for error messages and listings.
    private final Map<String, SimpleParser<O>> parserMap = new LinkedHashMap<>();
    private BiFunction<O, String, Object> defaultParser = null;

    public String parse(O object, String parameters) {
        SimpleParser<O> parser = parserMap.get(normalizeKey(parameters));

        Object result = parser == null ? null : parser.apply(object);
        if (result == null && defaultParser != null){
            result = defaultParser.apply(object, parameters);
        }

        return result == null ? null : String.valueOf(result);
    }

    private static String normalizeKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // Two names that differ only in case would silently shadow each other at lookup time, and which
    // one wins would depend on registration order; fail where the mistake is, at registration.
    private void register(String name, SimpleParser<O> parser) {
        SimpleParser<O> previous = parserMap.put(normalizeKey(name), parser);
        if (previous != null && !previous.getId().equals(name)) {
            parserMap.put(normalizeKey(name), previous);
            throw new IllegalArgumentException("Placeholder key collision: '" + name
                    + "' and '" + previous.getId() + "' differ only in case.");
        }
    }

    public Map<String, SimpleParser<O>> getParserMap() {
        return parserMap;
    }

    /**
     * A provider with the same registrations, in the same order, but its own map: registering on the
     * copy never reaches the original. The {@link SimpleParser} entries themselves are shared, which is
     * safe because they are immutable - and it keeps a parser's identity stable across a copy, so
     * anything keyed by that identity (a per-render memo, for one) still recognises it.
     */
    public PlaceholderProvider<O> copy() {
        PlaceholderProvider<O> copy = new PlaceholderProvider<>();
        copy.parserMap.putAll(this.parserMap);
        copy.defaultParser = this.defaultParser;
        return copy;
    }

    /**
     * Every key this provider answers for, mapped to its registered description ({@code ""} when
     * undescribed), in registration order. This is what an integrating plugin reads to list the
     * placeholders it can offer the user, so a key that is registered but not described still shows up.
     */
    public Map<String, String> describeAll() {
        Map<String, String> described = new LinkedHashMap<>();
        for (SimpleParser<O> parser : parserMap.values()) {
            described.put(parser.getId(), parser.getDescription());
        }
        return described;
    }

    public BiFunction<O, String, Object> getDefaultParser() {
        return defaultParser;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, Object parsedValue) {
        register(name, new SimpleParser<>(name, object -> parsedValue));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, String description, Object parsedValue) {
        register(name, new SimpleParser<>(name, description, object -> parsedValue));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, Function<O, Object> parser) {
        register(name, new SimpleParser<>(name, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, String description, Function<O, Object> parser) {
        register(name, new SimpleParser<>(name, description, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser) {
        this.defaultParser = defaultParser;
        return this;
    }

}
