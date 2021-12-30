package br.com.finalcraft.evernifecore.placeholder.base;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class PlaceholderProvider<O extends Object> {

    private final String identifier;
    private final Map<String, Function<O, Object>> simpleParsers = new HashMap<>();
    private Function<O, Object> genericParser = null;

    public PlaceholderProvider(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public PlaceholderProvider<O> addSimpleParser(Function<O, Object> value, String... property){
        for (String propName : property) {
            simpleParsers.put(propName, value);
        }
        return this;
    }

    public void setGenericParser(Function<O, Object> genericParser) {
        this.genericParser = genericParser;
    }

    public String parse(O object, String parameters) {
        Function<O, Object> simpleParser = simpleParsers.get(parameters);

        Object result = simpleParser.apply(object);
        if (result == null && genericParser != null){
            result = genericParser.apply(object);
        }

        return result == null ? null : String.valueOf(result);
    }

}
