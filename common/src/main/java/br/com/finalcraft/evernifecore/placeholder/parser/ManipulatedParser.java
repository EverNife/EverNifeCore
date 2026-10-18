package br.com.finalcraft.evernifecore.placeholder.parser;

import br.com.finalcraft.evernifecore.placeholder.manipulation.ManipulationContext;
import br.com.finalcraft.evernifecore.placeholder.manipulation.Manipulator;
import br.com.finalcraft.evernifecore.placeholder.manipulation.PlaceholderArgs;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nullable;

import java.util.function.BiFunction;

public class ManipulatedParser<O> {

    private final Manipulator manipulator;
    private final RegexReplacer<?> regexReplacer;
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
        return parse(object, placeholder, null);
    }

    /**
     * Runs the parser over {@code placeholder}. A {@code {key}} inside one of its blocks is answered by {@code owner}
     * with the same object. A block that breaks the grammar answers null (the placeholder stays as written); one whose
     * value the parser refuses answers {@link PlaceholderArgs.InvalidArgumentException#toVisibleText()}.
     */
    public String parse(O object, String placeholder, @Nullable RegexReplacer<O> owner){
        ManipulationContext context = this.regexReplacer != null
                ? new ManipulationContext.RContext<>(manipulator, placeholder, regexReplacer)
                : new ManipulationContext.SimpleContext(manipulator, placeholder);
        if (owner != null) {
            context.setNestedResolver(key -> {
                String quoted = owner.getClosures().quote(key);
                String resolved = owner.apply(quoted, object);
                return quoted.equals(resolved) ? null : resolved;
            });
        }

        Object result;
        try {
            result = parser.apply(object, context);
        } catch (PlaceholderArgs.SyntaxException unreadable) {
            return null;
        } catch (PlaceholderArgs.InvalidArgumentException refused) {
            return refused.toVisibleText();
        }

        return result == null ? null : String.valueOf(result);
    }

}
