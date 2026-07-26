package br.com.finalcraft.evernifecore.placeholder.replacer;

import br.com.finalcraft.evernifecore.placeholder.base.IProvider;
import br.com.finalcraft.evernifecore.placeholder.base.PlaceholderProvider;
import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.parser.ManipulatedParser;
import br.com.finalcraft.evernifecore.placeholder.parser.SimpleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexReplacer<O> implements Replacer<O>, IProvider<O>  {

    private final Closures closures;
    private final Pattern pattern;
    private final PlaceholderProvider<O> provider;
    private final List<ManipulatedParser<O>> manipulators = new ArrayList<>();

    public RegexReplacer() {
        this(Closures.PERCENT);
    }

    public RegexReplacer(final Closures closures) {
        this(closures, new PlaceholderProvider<>());
    }

    public RegexReplacer(final Closures closures, PlaceholderProvider<O> provider) {
        this.closures = closures;
        this.pattern = closures.getPattern();
        this.provider = provider;
    }

    public RegexReplacer(final Pattern pattern) {
        this(pattern, new PlaceholderProvider<>());
    }

    public RegexReplacer(Pattern pattern, PlaceholderProvider<O> provider) {
        Closures owner = Closures.ofPattern(pattern);
        // A replacer must know which delimiters it speaks, not just how to find them: callers that
        // hand over a raw pattern still get the closure it came from, so quoting a key round-trips.
        this.closures = owner == null ? Closures.PERCENT : owner;
        this.pattern = pattern;
        this.provider = provider;
    }

    public Closures getClosures() {
        return closures;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public PlaceholderProvider<O> getProvider() {
        return provider;
    }

    public List<ManipulatedParser<O>> getManipulators() {
        return manipulators;
    }

    /**
     * Every key this replacer answers for, mapped to its registered description ({@code ""} when
     * undescribed), in registration order. This is what an integrating plugin reads to list the
     * placeholders it can offer the user, so a key that is registered but not described still shows up.
     */
    public Map<String, String> describeAll() {
        Map<String, String> described = new LinkedHashMap<>();
        for (SimpleParser<O> parser : getProvider().getParserMap().values()) {
            described.put(parser.getId(), parser.getDescription());
        }
        return described;
    }

    public RegexReplacer<O> addManipulator(String manipulableString, BiFunction<O, ManipulationContext.SimpleContext, Object> parser){
        this.manipulators.add(
                new ManipulatedParser<>(
                        manipulableString,
                        parser
                )
        );
        //Sort manipulators based on the prefix lengh, it might help performance, bigger prefixes first
        Collections.sort(this.manipulators, Comparator.comparing(manipulatorParser -> {
            int prefixSize = manipulatorParser.getManipulator().getPrefix().length();
            long underlines = manipulatorParser.getId().chars().filter(c -> c == '_').count(); //More complex manipulators first
            return  (prefixSize * 1000) + underlines;
        }));
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
    public RegexReplacer<O> addParser(String name, Object parsedValue) {
        getProvider().addParser(name, parsedValue);
        return this;
    }

    @Override
    public RegexReplacer<O> addParser(String name, String description, Object parsedValue) {
        getProvider().addParser(name, description, parsedValue);
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

        final StringBuilder builder = new StringBuilder();
        int copiedUpTo = 0;   //everything before this index is already in the builder
        int searchFrom = 0;

        while (searchFrom <= text.length() && matcher.find(searchFrom)) {
            final String full_placeholder = matcher.group("key");

            String requested = this.getProvider().parse(object, full_placeholder);

            //Check the Manipulators, for overly complex placeholders
            if (requested == null && this.manipulators.size() > 0){
                for (ManipulatedParser<O> manipulatedParser : this.manipulators) {
                    if (manipulatedParser.getManipulator().match(full_placeholder)){
                        requested = manipulatedParser.parse(object, full_placeholder);
                        if (requested != null){
                            break;//can breka here as manipulators already return proper result
                        }
                    }
                }
            }

            if (requested != null){
                // The value is appended as-is: unlike Matcher.appendReplacement there is no
                // replacement syntax here, so a value like "C:\Users\x" or "$5 off" stays literal.
                builder.append(text, copiedUpTo, matcher.start()).append(requested);
                copiedUpTo = matcher.end();
                searchFrom = matcher.end();
            } else {
                // Nothing resolved this candidate, so its delimiters were not a placeholder pair -
                // e.g. the '%' of "100%" pairing with the opening '%' of the placeholder that
                // follows. Resume just after the opening one, so the real pair still gets its turn.
                searchFrom = matcher.start() + 1;
            }
        }

        return builder.append(text, copiedUpTo, text.length()).toString();
    }

    public CompoundReplacer compound(O object){
        return CompoundReplacer.from(this, object);
    }


    // -----------------------------------------------------------------------------------------------------------------
    //  Utility Methods
    // -----------------------------------------------------------------------------------------------------------------

    public RegexReplacer<O> addParser(String[] name, Function<O, Object> parser){
        for (String alias : name) {
            this.addParser(alias, parser);
        }
        return this;
    }

    public RegexReplacer<O> addParser(String[] name, String description, Function<O, Object> parser){
        for (String alias : name) {
            this.addParser(alias, description, parser);
        }
        return this;
    }
}
