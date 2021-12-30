package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexReplacer<O extends Object> implements Replacer<O> {

    private final Pattern pattern;
    private final Map<String, PlaceholderProvider<O>> PROVIDERS = new HashMap<>();
    private final PlaceholderProvider<O> DEFAULT_PROVIDER = new PlaceholderProvider<>(null);

    public RegexReplacer() {
        this(Closure.PERCENT.pattern);
    }

    public RegexReplacer(final Pattern pattern) {
        this.pattern = pattern;
    }

    public PlaceholderProvider<O> addProvider(PlaceholderProvider<O> provider){
        PROVIDERS.put(provider.getIdentifier(), provider);
        return provider;
    }

    public PlaceholderProvider<O> getDefaultProvider() {
        return DEFAULT_PROVIDER;
    }

    public RegexReplacer<O> addSimpleParser(Function<O, Object> value, String... property){
        getDefaultProvider().addSimpleParser(value, property);
        return this;
    }

    public RegexReplacer<O> setGenericParser(Function<O, Object> genericParser) {
        getDefaultProvider().setGenericParser(genericParser);
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

            PlaceholderProvider<O> provider = PROVIDERS.get(identifier);
            final String requested;
            if (provider != null){
                requested = provider.parse(object, parameters);
            }else {
                requested = DEFAULT_PROVIDER.parse(object, identifier + "_" + parameters); //Default Provider will ignore identifier
            }

            matcher.appendReplacement(builder, requested != null ? requested : matcher.group(0));
        }
        while (matcher.find());

        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(builder).toString());
    }

}
