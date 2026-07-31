package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * The two shapes an argument name can have. Whether a value may come from the sender instead of the
 * line is NOT one of them: that is {@code @Arg(fromSender = true)}, an annotation attribute, so the
 * displayed name never has to carry semantics the framework has to tokenize back out of it.
 */
public enum ArgRequirementType {
    REQUIRED("<", ">", true),
    OPTIONAL("[", "]", false),
    ;

    private final String start;
    private final String end;
    private final boolean required;

    ArgRequirementType(String start, String end, boolean required) {
        this.start = start;
        this.end = end;
        this.required = required;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public boolean isRequired() {
        return required;
    }

    public static String stripBrackets(@Nonnull String argument){
        for (ArgRequirementType requirementType : values()) {
            if (argument.startsWith(requirementType.getStart()) && argument.endsWith(requirementType.getEnd())) {
                return argument.substring(requirementType.getStart().length(), argument.length() - requirementType.getEnd().length());
            }
        }
        return argument;
    }

    public static @Nullable ArgRequirementType getArgumentType(@Nonnull String argument){

        if (argument.length() >= 2) {
            for (ArgRequirementType requirementType : values()) {
                if (argument.startsWith(requirementType.getStart()) && argument.endsWith(requirementType.getEnd())) {
                    return requirementType;
                }
            }
        }

        return null;
    }

}
