package br.com.finalcraft.evernifecore.placeholder.parser;

import java.util.function.Function;

public class SimpleParser<O extends Object> {

    private final String id;
    private final String description;
    private final Function<O, Object> parser;

    public SimpleParser(String id, String description, Function<O, Object> parser) {
        this.id = id;
        this.description = description;
        this.parser = parser;
    }

    public SimpleParser(String id, Function<O, Object> parser) {
        this.id = id;
        this.description = "";
        this.parser = parser;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Function<O, Object> getParser() {
        return parser;
    }

    public Object apply(O object){
        return parser.apply(object);
    }

}
