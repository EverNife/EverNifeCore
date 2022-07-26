package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.base.IProvider;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexReplacer<O extends Object> implements Replacer<O>, IProvider<O>  {

    private final Pattern pattern;
    private final Map<String, PlaceholderProvider<O>> PROVIDERS = new HashMap<>();
    private final PlaceholderProvider<O> DEFAULT_PROVIDER = new PlaceholderProvider<>(null);

    public RegexReplacer() {
        this(Closure.PERCENT.pattern);
    }

    public RegexReplacer(final Pattern pattern) {
        this.pattern = pattern;
    }

    public PlaceholderProvider<O> addProvider(String providerID){
        return addProvider(new PlaceholderProvider(providerID));
    }

    public PlaceholderProvider<O> addProvider(PlaceholderProvider<O> provider){
        PROVIDERS.put(provider.getProviderID(), provider);
        return provider;
    }

    public PlaceholderProvider<O> getDefaultProvider() {
        return DEFAULT_PROVIDER;
    }

    @Override
    public RegexReplacer<O> addMappedParser(String name, Function<O, Object> parser) {
        getDefaultProvider().addMappedParser(name, parser);
        return this;
    }

    @Override
    public RegexReplacer<O> addMappedParser(String name, String description, Function<O, Object> parser) {
        getDefaultProvider().addMappedParser(name, description, parser);
        return this;
    }

    @Override
    public RegexReplacer<O> setDefaultParser(BiFunction<O, String, Object> defaultParser) {
        getDefaultProvider().setDefaultParser(defaultParser);
        return this;
    }

    @Override
    public String apply(final String text, final O object) {
        final Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) { //No Closure found ( no %% or {} found )
            return text;
        }

        final StringBuffer builder = new StringBuffer();

        do {
            final String identifier = matcher.group("identifier");
            final String parameters = matcher.group("parameters");

            PlaceholderProvider<O> provider = identifier == null ? null : PROVIDERS.get(identifier);
            final String requested;
            if (provider != null){
                requested = provider.parse(object, parameters);
            }else {
                String prefix = (identifier != null ? (identifier + "_") : "");
                requested = DEFAULT_PROVIDER.parse(object, prefix + parameters); //Default Provider will ignore identifier
            }

            matcher.appendReplacement(builder, requested != null ? requested : matcher.group(0));
        }
        while (matcher.find());

        return matcher.appendTail(builder).toString();
    }

    public CompoundReplacer compound(O object){
        return CompoundReplacer.from(this, object);
    }

}
