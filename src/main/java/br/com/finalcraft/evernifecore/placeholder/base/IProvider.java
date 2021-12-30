package br.com.finalcraft.evernifecore.placeholder.base;

import java.util.function.Function;

public interface IProvider<O extends Object> {

    public IProvider<O> addMappedParser(String name, Function<O, Object> parser);

    public IProvider<O> addMappedParser(String name, String description, Function<O, Object> parser);

    public IProvider<O> setDefaultParser(Function<O, Object> parser);

}
