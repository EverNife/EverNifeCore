package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.context;

import java.util.function.Function;

public class ArgContextExtractor<O> {

    private final String id;
    private final Function<String, O> extractor;

    public ArgContextExtractor(String id, Function<String, O> extractor) {
        this.id = id;
        this.extractor = extractor;
    }

    public static ArgContextExtractor<Boolean> of(String id) {
        return new ArgContextExtractor<>(id.toLowerCase(), Boolean::parseBoolean);
    }

    public static <O> ArgContextExtractor<O> of(String id, Function<String, O> extractor) {
        return new ArgContextExtractor<>(id.toLowerCase(), extractor);
    }

    public String getId() {
        return id;
    }

    public Function<String, O> getExtractor() {
        return extractor;
    }

}
