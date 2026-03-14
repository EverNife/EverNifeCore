package br.com.finalcraft.evernifecore.placeholder.base;

import br.com.finalcraft.evernifecore.placeholder.parser.SimpleParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PlaceholderProvider<O extends Object> implements IProvider<O>{

    private final Map<String, SimpleParser> parser_map = new LinkedHashMap<>();
    private BiFunction<O, String, Object> default_parser = null;

    public String parse(O object, String parameters) {
        SimpleParser parser = parser_map.get(parameters);

        Object result = parser == null ? null : parser.apply(object);
        if (result == null && default_parser != null){
            result = default_parser.apply(object, parameters);
        }

        return result == null ? null : String.valueOf(result);
    }

    public Map<String, SimpleParser> getParserMap() {
        return parser_map;
    }

    public BiFunction<O, String, Object> getDefaultParser() {
        return default_parser;
    }

    @Override
    public IProvider<O> addParser(String name, Object parsedValue) {
        parser_map.put(name, new SimpleParser(name, object -> parsedValue));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, Function<O, Object> parser) {
        parser_map.put(name, new SimpleParser(name, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addParser(String name, String description, Function<O, Object> parser) {
        parser_map.put(name, new SimpleParser(name, description, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser) {
        this.default_parser = defaultParser;
        return this;
    }

}
