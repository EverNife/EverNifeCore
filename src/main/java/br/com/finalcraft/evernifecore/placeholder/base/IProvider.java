package br.com.finalcraft.evernifecore.placeholder.base;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface IProvider<O extends Object> {

    public IProvider<O> addParser(String name, Function<O, Object> parser);

    public IProvider<O> addParser(String name, String description, Function<O, Object> parser);

    public IProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser);

}
