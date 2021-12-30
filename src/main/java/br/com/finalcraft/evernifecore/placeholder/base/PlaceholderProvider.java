package br.com.finalcraft.evernifecore.placeholder.base;

import br.com.finalcraft.evernifecore.placeholder.parser.SimpleParser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class PlaceholderProvider<O extends Object> implements IProvider<O>{

    private final String provider_id;
    private final Map<String, SimpleParser> parser_map = new HashMap<>();
    private SimpleParser generic_parser = null;

    public PlaceholderProvider(String providerID) {
        this.provider_id = providerID;
    }

    public String getProviderID() {
        return provider_id;
    }

    public String parse(O object, String parameters) {
        SimpleParser parser = parser_map.get(parameters);

        Object result = parser.apply(object);
        if (result == null && generic_parser != null){
            result = generic_parser.apply(object);
        }

        return result == null ? null : String.valueOf(result);
    }

    @Override
    public PlaceholderProvider<O> addMappedParser(String name, Function<O, Object> parser) {
        parser_map.put(name, new SimpleParser(name, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> addMappedParser(String name, String description, Function<O, Object> parser) {
        parser_map.put(name, new SimpleParser(name, description, parser));
        return this;
    }

    @Override
    public PlaceholderProvider<O> setDefaultParser(Function<O, Object> parser) {
        this.generic_parser = new SimpleParser("",parser);
        return this;
    }

}
