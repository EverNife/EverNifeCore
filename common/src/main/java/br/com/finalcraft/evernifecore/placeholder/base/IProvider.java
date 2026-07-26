package br.com.finalcraft.evernifecore.placeholder.base;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Anything a placeholder key can be registered on. Implementations narrow every return type to their
 * own, so a chain that starts with any of these overloads can go on calling whatever that
 * implementation adds on top.
 */
public interface IProvider<O> {

    IProvider<O> addParser(String name, Object parsedValue);

    /** A constant value that also shows up in the public placeholder listing under {@code description}. */
    IProvider<O> addParser(String name, String description, Object parsedValue);

    IProvider<O> addParser(String name, Function<O, Object> parser);

    IProvider<O> addParser(String name, String description, Function<O, Object> parser);

    IProvider<O> setDefaultParser(BiFunction<O, String, Object> defaultParser);

}
