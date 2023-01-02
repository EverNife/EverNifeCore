package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.base.IProvider;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.parser.ManipulatedParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexReplacer<O extends Object> implements Replacer<O>, IProvider<O>  {

    private final Pattern pattern;
    private final PlaceholderProvider<O> provider = new PlaceholderProvider<>();
    private final List<ManipulatedParser<O>> manipulators = new ArrayList<>();

    public RegexReplacer() {
        this(Closures.PERCENT.getPattern());
    }

    public RegexReplacer(final Pattern pattern) {
        this.pattern = pattern;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public PlaceholderProvider<O> getProvider() {
        return provider;
    }

    public RegexReplacer<O> addManipulator(String manipulableString, BiFunction<O, ManipulationContext.SimpleContext, Object> parser){
        this.manipulators.add(
                new ManipulatedParser<>(
                        manipulableString,
                        parser
                )
        );
        //Sort manipulators based on the prefix lengh, it might help performance, bigger prefixes first
        Collections.sort(this.manipulators, Comparator.comparing(manipulatorParser -> manipulatorParser.getManipulator().getPrefix().length()));
        Collections.reverse(this.manipulators);
        return this;
    }

    public <RC> RegexReplacer<O> addManipulator(String manipulableString, RegexReplacer<RC> regexReplacer, BiFunction<O, ManipulationContext.RContext<RC>, Object> parser){
        this.manipulators.add(
                new ManipulatedParser<>(
                        manipulableString,
                        regexReplacer,
                        parser
                )
        );
        //Sort manipulators based on the prefix lengh, it might help performance, bigger prefixes first
        Collections.sort(this.manipulators, Comparator.comparing(manipulatorParser -> manipulatorParser.getManipulator().getPrefix().length()));
        Collections.reverse(this.manipulators);
        return this;
    }

    @Override
    public RegexReplacer<O> addParser(String name, Function<O, Object> parser) {
        getProvider().addParser(name, parser);
        return this;
    }

    @Override
    public RegexReplacer<O> addParser(String name, String description, Function<O, Object> parser) {
        getProvider().addParser(name, description, parser);
        return this;
    }

    @Override
    public RegexReplacer<O> setDefaultParser(BiFunction<O, String, Object> defaultParser) {
        getProvider().setDefaultParser(defaultParser);
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
            final String full_placeholder = (identifier != null ? (identifier + "_") : "") + parameters;

            String requested = null; //Store the result of this placeholder, or null in case there is no match

            //First check the Manipulators, for overly complex placeholders
            if (this.manipulators.size() > 0){
                for (ManipulatedParser<O> manipulatedParser : this.manipulators) {
                    if (manipulatedParser.getManipulator().match(full_placeholder)){
                        requested = manipulatedParser.parse(object, full_placeholder);
                        break;
                    }
                }
            }

            if (requested == null){
                requested = this.provider.parse(object, full_placeholder); //Default Provider will ignore identifier
            }

            if (requested != null){
                requested = requested.replace("$", "\\$"); // '$' needs to be escaped in replacement to prevent 'Illegal group reference'
                matcher.appendReplacement(builder, requested);
            }
        }
        while (matcher.find());

        return matcher.appendTail(builder).toString();
    }

    public CompoundReplacer compound(O object){
        return CompoundReplacer.from(this, object);
    }

}
