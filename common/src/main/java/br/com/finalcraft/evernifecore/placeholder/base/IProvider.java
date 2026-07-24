package br.com.finalcraft.evernifecore.placeholder.base;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface IProvider<O> {

    IProvider<O> addParser(String name, Object parsedValue);

    IProvider<O> addParser(String name, Function<O, Object> parser);

    IProvider<O> addParser(String name, String description, Function<O, Object> parser);

    IProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser);

}
