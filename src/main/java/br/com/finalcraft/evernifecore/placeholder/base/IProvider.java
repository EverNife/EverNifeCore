package br.com.finalcraft.evernifecore.placeholder.base;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface IProvider<O extends Object> {

    public default IProvider<O> addParser(String name, Object obj){
        this.addParser(name, o -> obj);
        return this;
    }

    public default IProvider<O> addParser(String[] name, Object obj){
        for (String alias : name) {
            this.addParser(alias, o -> obj);
        }
        return this;
    }

    public IProvider<O> addParser(String name, Function<O, Object> parser);

    public default IProvider<O> addParser(String[] name, Function<O, Object> parser){
        for (String alias : name) {
            this.addParser(alias, parser);
        }
        return this;
    }

    public default IProvider<O> addParser(String name, String description, Object obj){
        this.addParser(name, description, o -> obj);
        return this;
    }


    public default IProvider<O> addParser(String[] name, String description, Object obj){
        for (String alias : name) {
            this.addParser(alias, description, o -> obj);
        }
        return this;
    }

    public IProvider<O> addParser(String name, String description, Function<O, Object> parser);

    public default IProvider<O> addParser(String[] name, String description, Function<O, Object> parser){
        for (String alias : name) {
            this.addParser(alias, description, parser);
        }
        return this;
    }

    public IProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser);

}
