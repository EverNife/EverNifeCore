package br.com.finalcraft.evernifecore.placeholder.parser;

import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.manipulation.Manipulator;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

public class ManipulatedParser<O extends Object> {

    private final Manipulator manipulator;
    private final RegexReplacer regexReplacer;
    private final BiFunction<O, ManipulationContext, Object> parser;

    public <C> ManipulatedParser(String id, RegexReplacer<C> regexReplacer, BiFunction<O, ManipulationContext.RContext<C>, Object> parser) {
        this.manipulator = new Manipulator(id);
        this.regexReplacer = regexReplacer;
        this.parser = (BiFunction<O, ManipulationContext, Object>) (BiFunction) parser;
    }

    public ManipulatedParser(String id, BiFunction<O, ManipulationContext.SimpleContext, Object> parser) {
        this.manipulator = new Manipulator(id);
        this.regexReplacer = null;
        this.parser = (BiFunction<O, ManipulationContext, Object>) (BiFunction) parser;
    }

    public Manipulator getManipulator() {
        return manipulator;
    }

    public String getId() {
        return this.manipulator.getOriginalText();
    }

    public @Nullable RegexReplacer getRegexReplacer() {
        return regexReplacer;
    }

    public String parse(O object, String placeholder){
        ManipulationContext context = this.regexReplacer != null
                ? new ManipulationContext.RContext<>(manipulator, placeholder, regexReplacer)
                : new ManipulationContext.SimpleContext(manipulator, placeholder);

        Object result = parser.apply(object, context);

        return result == null ? null : String.valueOf(result);
    }

}
