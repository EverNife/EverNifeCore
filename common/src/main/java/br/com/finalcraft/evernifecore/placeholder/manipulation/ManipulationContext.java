package br.com.finalcraft.evernifecore.placeholder.manipulation;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class ManipulationContext {

    private final Manipulator manipulator;
    private final String inputText;
    private final HashMap<String, String> manipulationResult = new LinkedHashMap<>();
    private @Nullable Function<String, String> nestedResolver;

    public ManipulationContext(Manipulator manipulator, String inputText) {
        this.manipulator = manipulator;
        this.inputText = inputText;

        if (manipulator.hasBlocks()) {
            Map<String, String> extracted = manipulator.extract(inputText);
            if (extracted != null) {
                manipulationResult.putAll(extracted);
            }
            return;
        }

        for (String delimiter : manipulator.getDelimiters()) {
            //delimiters are raw tokens, not regexes; quote so metacharacters match literally
            inputText = inputText.replaceFirst(Pattern.quote(delimiter),"\uFFFF");
        }

        List<String> inputClosures = Arrays.asList(inputText.split("\uFFFF"))
                .stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        for (int i = 0; i < inputClosures.size(); i++) {
            String originalClosure = manipulator.getClosures().get(i);
            String inputClosure = inputClosures.get(i);

            manipulationResult.put(originalClosure, inputClosure);
        }
    }

    public Manipulator getManipulator() {
        return manipulator;
    }

    public String getInputText() {
        return inputText;
    }

    public HashMap<String, String> getManipulationResult() {
        return manipulationResult;
    }

    public @Nonnull String getString(String closure){
        if (!manipulationResult.containsKey(closure)) {
            throw new IllegalArgumentException(String.format("Tried to retrieve a closure from the ManipulationContext that does not belong to this context.\nTried to retrieve '%s' from [%s]", closure, manipulationResult.keySet().stream().collect(Collectors.joining(", "))));
        }

        return manipulationResult.get(closure);
    }

    public @Nonnull Argumento getArgumento(String closure){
        if (!manipulationResult.containsKey(closure)) {
            throw new IllegalArgumentException(String.format("Tried to retrieve a closure from the ManipulationContext that does not belong to this context.\nTried to retrieve '%s' from [%s]", closure, manipulationResult.keySet().stream().collect(Collectors.joining(", "))));
        }

        return new Argumento(manipulationResult.get(closure));
    }

    /**
     * The block {@code closure} ({@code "[options]"}, also for an optional {@code [options?]}) read by
     * {@link PlaceholderArgs}, with its {@code {key}}s resolved by the replacer that owns this placeholder; empty when
     * an optional block was left out.
     *
     * @throws IllegalArgumentException when the template has no such block, or the block breaks the grammar
     */
    public @Nonnull PlaceholderArgs getArgs(String closure){
        if (!manipulator.getClosures().contains(closure) || !closure.startsWith("[")) {
            throw new IllegalArgumentException(String.format("Tried to read the block '%s', but the template '%s' has the closures [%s].", closure, manipulator.getOriginalText(), String.join(", ", manipulator.getClosures())));
        }
        String block = closure.substring(1, closure.length() - 1);
        String content = manipulationResult.get(closure);
        return content == null ? PlaceholderArgs.empty(block) : PlaceholderArgs.parse(block, content, nestedResolver);
    }

    /** Internal: how a {@code {key}} inside a block is answered - set by whoever parses with this context. */
    public void setNestedResolver(@Nullable Function<String, String> nestedResolver) {
        this.nestedResolver = nestedResolver;
    }

    public static class SimpleContext extends ManipulationContext{

        public SimpleContext(Manipulator manipulator, String inputText) {
            super(manipulator, inputText);
        }

    }

    public static class RContext<O> extends ManipulationContext{
        private final RegexReplacer<O> regexReplacer;

        public RContext(Manipulator manipulator, String inputText, @Nullable RegexReplacer regexReplacer) {
            super(manipulator, inputText);
            this.regexReplacer = regexReplacer;
        }

        public RegexReplacer<O> getRegexReplacer() {
            return regexReplacer;
        }

        public String quoteAndParse(O object, String unquotedPlaceholder){
            String quoted = this.regexReplacer.getClosures().quote(unquotedPlaceholder);
            String result = this.regexReplacer.apply(quoted, object);
            return quoted.equals(result) ? null : result; //As this is an intern call, it should return null in case of fail
        }
    }

}

