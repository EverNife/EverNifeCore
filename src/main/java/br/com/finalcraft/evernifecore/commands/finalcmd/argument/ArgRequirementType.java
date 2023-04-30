package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ArgRequirementType {
    REQUIRED_OR_PROVIDED_BY_CONTEXT("<(", ")>", true, true),
    OPTIONAL_OR_PROVIDED_BY_CONTEXT("[(", "]]", false, true),
    REQUIRED("<", ">", true, false),
    OPTIONAL("[", "]", false, false),
    ;

    private final String start;
    private final String end;
    private final boolean required;
    private final boolean providedByContext;

    ArgRequirementType(String start, String end, boolean required, boolean providedByContext) {
        this.start = start;
        this.end = end;
        this.required = required;
        this.providedByContext = providedByContext;
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

    public boolean isProvidedByContext() {
        return providedByContext;
    }

    public static String stripBrackets(@NotNull String argument){
        for (ArgRequirementType requirementType : values()) {
            if (argument.startsWith(requirementType.getStart()) && argument.endsWith(requirementType.getEnd())) {
                return argument.substring(requirementType.getStart().length(), argument.length() - requirementType.getEnd().length());
            }
        }
        return argument;
    }

    public static @Nullable ArgRequirementType getArgumentType(@NotNull String argument){

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
