package br.com.finalcraft.evernifecore.placeholder.base;

import br.com.finalcraft.evernifecore.placeholder.parser.SimpleParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PlaceholderProvider<O extends Object> implements IProvider<O>{

    // Keys are indexed lower-cased so a placeholder resolves regardless of how it was typed in the
    // text; SimpleParser keeps the name as registered, for error messages and listings.
    private final Map<String, SimpleParser> parser_map = new LinkedHashMap<>();
    private BiFunction<O, String, Object> default_parser = null;

    public String parse(O object, String parameters) {
        SimpleParser parser = parser_map.get(normalizeKey(parameters));

        Object result = parser == null ? null : parser.apply(object);
        if (result == null && default_parser != null){
            result = default_parser.apply(object, parameters);
        }

        return result == null ? null : String.valueOf(result);
    }

    private static String normalizeKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // Two names that differ only in case would silently shadow each other at lookup time, and which
    // one wins would depend on registration order; fail where the mistake is, at registration.
    private void register(String name, SimpleParser parser) {
        SimpleParser previous = parser_map.put(normalizeKey(name), parser);
        if (previous != null && !previous.getId().equals(name)) {
            parser_map.put(normalizeKey(name), previous);
            throw new IllegalArgumentException("Placeholder key collision: '" + name
                    + "' and '" + previous.getId() + "' differ only in case.");
        }
    }

    public Map<String, SimpleParser> getParserMap() {
        return parser_map;
    }

    public BiFunction<O, String, Object> getDefaultParser() {
        return default_parser;
    }

    @Override
    public IProvider<O> addParser(String name, Object parsedValue) {
        register(name, new SimpleParser(name, object -> parsedValue));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, Function<O, Object> parser) {
        register(name, new SimpleParser(name, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, String description, Function<O, Object> parser) {
        register(name, new SimpleParser(name, description, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser) {
        this.default_parser = defaultParser;
        return this;
    }

}
