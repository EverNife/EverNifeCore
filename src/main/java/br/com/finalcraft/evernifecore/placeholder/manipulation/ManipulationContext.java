package br.com.finalcraft.evernifecore.placeholder.manipulation;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ManipulationContext {

    private final Manipulator manipulator;
    private final String inputText;
    private final HashMap<String, String> manipulationResult = new LinkedHashMap<>();

    public ManipulationContext(Manipulator manipulator, String inputText) {
        this.manipulator = manipulator;
        this.inputText = inputText;

        for (String delimiter : manipulator.getDelimiters()) {
            inputText = inputText.replaceFirst(delimiter,"\uFFFF");
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

    public @NotNull String getString(String closure){
        if (!manipulationResult.containsKey(closure)) {
            throw new IllegalArgumentException(String.format("Tried to retrieve a closure from the ManipulationContext that does not belong to this context.\nTried to retrieve '%s' from [%s]", closure, closure));
        }

        return manipulationResult.get(closure);
    }

    public @NotNull Argumento getArgumento(String closure){
        if (!manipulationResult.containsKey(closure)) {
            throw new IllegalArgumentException(String.format("Tried to retrieve a closure from the ManipulationContext that does not belong to this context.\nTried to retrieve '%s' from [%s]", closure, closure));
        }

        return new Argumento(manipulationResult.get(closure));
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
            String quoted = Closures.PERCENT.quote(unquotedPlaceholder);
            String result = this.regexReplacer.apply(quoted, object);
            return quoted.equals(result) ? null : result; //As this is an intern call, it should return null in case of fail
        }
    }

}

