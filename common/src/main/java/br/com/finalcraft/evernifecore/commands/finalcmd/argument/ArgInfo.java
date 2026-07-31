package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Everything the framework knows about one declared argument, whatever family it belongs to. The
 * {@link ArgSource} is what keeps the families apart: only a positional one sits at a position, so
 * only a positional one answers {@link #getIndex()}.
 * <p>
 * There is no public constructor on purpose - a source and an index have to agree, and a factory per
 * source is what makes an unanswerable combination impossible to write.
 */
//The generated toString/equals read the fields directly: going through the getters would make
//printing a non-positional argument throw
@Data
@ToString(doNotUseGetters = true)
@EqualsAndHashCode(doNotUseGetters = true)
public class ArgInfo {

    /** The index a source that has none carries - unreachable through {@link #getIndex()}. */
    private static final int NO_INDEX = -1;

    private final Class argumentType;
    private final ArgData argData;
    private final ArgSource source;
    /** Local to the executable's own window: the first positional of any method sits at 0. */
    private final int index;
    private final ArgRequirementType requirementType;
    private final boolean greedy;

    private ArgInfo(Class argumentType, ArgData argData, ArgSource source, int index, ArgRequirementType requirementType, boolean greedy) {
        this.argumentType = argumentType;
        this.argData = argData;
        this.source = source;
        this.index = index;
        this.requirementType = requirementType;
        this.greedy = greedy;
    }

    /** An argument that eats the token at {@code index} of its method's own window. */
    public static ArgInfo positional(Class argumentType, ArgData argData, int index, ArgRequirementType requirementType) {
        return positional(argumentType, argData, index, requirementType, false);
    }

    /** @see #positional(Class, ArgData, int, ArgRequirementType) */
    public static ArgInfo positional(Class argumentType, ArgData argData, int index, ArgRequirementType requirementType, boolean greedy) {
        if (index < 0){
            throw new IllegalArgumentException("A positional argument sits at a position: [" + argData.getName() + "] was given index " + index);
        }
        return new ArgInfo(argumentType, argData, ArgSource.POSITIONAL, index, requirementType, greedy);
    }

    /**
     * A {@code --name value} flag. It takes no requirement of its own: a flag name is not bracket-quoted
     * like an {@code @Arg} is, so there is nothing to read one out of. REQUIRED here describes the
     * VALUE - a flag that WAS spelled out and whose value the parser cannot read is an error instead of
     * a silent null, while the flag's own presence is never required.
     */
    public static ArgInfo flag(Class argumentType, ArgData argData) {
        return new ArgInfo(argumentType, argData, ArgSource.FLAG, NO_INDEX, ArgRequirementType.REQUIRED, false);
    }

    /** A parameter read off the invocation. Nothing routes above it, so it is always required. */
    public static ArgInfo contextual(Class argumentType, ArgData argData) {
        return new ArgInfo(argumentType, argData, ArgSource.CONTEXTUAL, NO_INDEX, ArgRequirementType.REQUIRED, false);
    }

    /** A value that never came from a command line - a config entry, or a parser exercised on its own. */
    public static ArgInfo standalone(Class argumentType, ArgData argData) {
        return new ArgInfo(argumentType, argData, ArgSource.STANDALONE, NO_INDEX, ArgRequirementType.REQUIRED, false);
    }

    /** What a parser that delegates to another one over the same argument hands it. */
    public ArgInfo deriveFor(Class<?> otherType) {
        return new ArgInfo(otherType, argData, source, index, requirementType, greedy);
    }

    /**
     * @throws IllegalStateException when this argument occupies no position - asking is the bug, and a
     * fabricated number would only move it somewhere harder to see
     */
    public int getIndex() {
        if (source != ArgSource.POSITIONAL){
            //An unannotated parameter has no declared name, and then its type is the only handle on it
            String named = argData.getName().isEmpty() ? argumentType.getSimpleName() : argData.getName();
            throw new IllegalStateException("[" + named + "] is " + source + ", so it sits at no position on the line. "
                    + "Read getSource() to tell the families apart, or deriveFor(Class) to hand this same argument to another parser.");
        }
        return index;
    }

    public boolean isRequired(){
        return requirementType.isRequired();
    }

    /** Whether the parser may answer from the sender's own state when the token is absent. */
    public boolean isProvidedByContext(){
        return argData.isFromSender();
    }

    /** Whether this argument takes every token left instead of exactly one. */
    public boolean isGreedy(){
        return greedy;
    }
}
